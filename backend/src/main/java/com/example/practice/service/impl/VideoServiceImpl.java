package com.example.practice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.practice.common.BusinessException;
import com.example.practice.constant.NotificationType;
import com.example.practice.constant.VideoStatus;
import com.example.practice.entity.User;
import com.example.practice.entity.UserFollow;
import com.example.practice.entity.Video;
import com.example.practice.entity.VideoCoin;
import com.example.practice.entity.VideoComment;
import com.example.practice.entity.VideoFavorite;
import com.example.practice.entity.WatchHistory;
import com.example.practice.mapper.UserFollowMapper;
import com.example.practice.mapper.UserMapper;
import com.example.practice.mapper.VideoCoinMapper;
import com.example.practice.mapper.VideoCommentMapper;
import com.example.practice.mapper.VideoFavoriteMapper;
import com.example.practice.mapper.VideoMapper;
import com.example.practice.mapper.WatchHistoryMapper;
import com.example.practice.service.NotificationService;
import com.example.practice.service.VideoService;
import com.example.practice.util.UploadUtils;
import com.example.practice.vo.CommentVO;
import com.example.practice.vo.VideoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 视频服务实现
 * 模块 1：文件上传落盘 + 记录入库 + 列表/详情 + 简单审核
 * 模块 2：播放量计数（Redis 增量 + 定时落库）
 * 模块 3：互动体系（点赞/投币/收藏 + 评论楼中楼）
 * 模块 5：个人中心数据（我的视频/收藏/历史）
 */
@Slf4j
@Service
public class VideoServiceImpl implements VideoService {

    /** Redis key 前缀：播放增量（值=未落库的播放次数） */
    private static final String KEY_PLAY = "video:play:";

    /** Redis key 前缀：点赞用户集合 */
    private static final String KEY_LIKE = "video:like:";

    /** Redis key 前缀：点赞计数增量 */
    private static final String KEY_LIKE_COUNT = "video:like:count:";

    /** 点赞用户集合的过期时间：30 天（到期后 liked 状态重置，防止 key 无限膨胀） */
    private static final Duration LIKE_SET_TTL = Duration.ofDays(30);

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private VideoCommentMapper commentMapper;

    @Autowired
    private VideoFavoriteMapper favoriteMapper;

    @Autowired
    private WatchHistoryMapper historyMapper;

    @Autowired
    private VideoCoinMapper coinMapper;

    @Autowired
    private UserFollowMapper followMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UploadUtils uploadUtils;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // ==================== 模块1：上传 / 查询 / 审核 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoVO upload(Long userId, MultipartFile file, MultipartFile cover,
                          String title, String description, Integer category) {
        if (!StringUtils.hasText(title)) {
            throw new BusinessException("视频标题不能为空");
        }
        if (title.length() > 100) {
            throw new BusinessException("视频标题不能超过 100 字");
        }

        String videoUrl = uploadUtils.saveVideo(file);
        String coverUrl = (cover == null || cover.isEmpty()) ? null : uploadUtils.saveImage(cover);

        Video video = new Video();
        video.setUserId(userId);
        video.setTitle(title.trim());
        video.setDescription(description == null ? "" : description.trim());
        video.setVideoUrl(videoUrl);
        video.setCoverUrl(coverUrl);
        video.setCategory(category == null ? 0 : category);
        video.setStatus(VideoStatus.PENDING);
        video.setPlayCount(0);
        video.setLikeCount(0);
        video.setFavoriteCount(0);
        video.setCoinCount(0);
        try {
            videoMapper.insert(video);
        } catch (RuntimeException e) {
            // 入库失败：清理刚写入磁盘的文件，避免留下"无记录的孤儿文件"
            uploadUtils.deleteIfExists(videoUrl);
            uploadUtils.deleteIfExists(coverUrl);
            throw e;
        }
        return VideoVO.from(video, null);
    }

    @Override
    public IPage<VideoVO> list(long page, long size, Integer category, String sort) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getStatus, VideoStatus.PUBLISHED);
        if (category != null) {
            wrapper.eq(Video::getCategory, category);
        }
        // 排序：hot=按播放量（最热），默认按发布时间（最新）
        if ("hot".equals(sort)) {
            wrapper.orderByDesc(Video::getPlayCount).orderByDesc(Video::getCreateTime);
        } else {
            wrapper.orderByDesc(Video::getCreateTime);
        }
        Page<Video> p = videoMapper.selectPage(new Page<>(page, size), wrapper);
        return p.convert(this::toVO);
    }

    @Override
    public IPage<VideoVO> search(String keyword, long page, long size, String sort) {
        // 关键词：去空格后 LIKE 模糊匹配标题；空关键词等同查全部（返回最新列表）
        String kw = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getStatus, VideoStatus.PUBLISHED);
        if (StringUtils.hasText(kw)) {
            wrapper.like(Video::getTitle, kw);
        }
        // 排序：hot=按播放量（最热），默认按发布时间（最新）
        if ("hot".equals(sort)) {
            wrapper.orderByDesc(Video::getPlayCount).orderByDesc(Video::getCreateTime);
        } else {
            wrapper.orderByDesc(Video::getCreateTime);
        }
        Page<Video> p = videoMapper.selectPage(new Page<>(page, size), wrapper);
        return p.convert(this::toVO);
    }

    @Override
    public IPage<VideoVO> feed(Long userId, long page, long size) {
        // 1. 查我关注的用户 ID 列表（拉模式 Feed：实时查关注关系，数据量小够用）
        List<UserFollow> follows = followMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId));
        List<Long> followingIds = follows.stream().map(UserFollow::getFollowingId).collect(Collectors.toList());
        // 一个都没关注：返回空分页，避免 IN () 语法错误
        if (followingIds.isEmpty()) {
            return new Page<>(page, size);
        }

        // 2. 查这些用户发布的视频，按发布时间倒序分页
        Page<Video> p = videoMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getStatus, VideoStatus.PUBLISHED)
                        .in(Video::getUserId, followingIds)
                        .orderByDesc(Video::getCreateTime));
        return p.convert(this::toVO);
    }

    @Override
    public IPage<VideoVO> listByUser(Long userId, long page, long size) {
        // 他人主页：只展示已发布视频（待审核/驳回不对外）
        Page<Video> p = videoMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getUserId, userId)
                        .eq(Video::getStatus, VideoStatus.PUBLISHED)
                        .orderByDesc(Video::getCreateTime));
        return p.convert(this::toVO);
    }

    @Override
    public VideoVO detail(Long id, Long currentUserId) {
        Video video = videoMapper.selectById(id);
        if (video == null) {
            throw new BusinessException("视频不存在");
        }
        boolean published = video.getStatus().equals(VideoStatus.PUBLISHED);
        boolean isOwner = currentUserId != null && currentUserId.equals(video.getUserId());
        if (!published && !isOwner) {
            throw new BusinessException("视频未发布，仅作者可见");
        }

        VideoVO vo = toVO(video);
        // 播放量 = 数据库值 + Redis 未落库增量（准实时）
        String inc = stringRedisTemplate.opsForValue().get(KEY_PLAY + id);
        if (inc != null) {
            vo.setPlayCount(video.getPlayCount() + Integer.parseInt(inc));
        }
        // 点赞数 = 数据库值 + Redis 未落库增量
        String likeInc = stringRedisTemplate.opsForValue().get(KEY_LIKE_COUNT + id);
        if (likeInc != null) {
            vo.setLikeCount(video.getLikeCount() + Integer.parseInt(likeInc));
        }
        // 当前用户是否已点赞（登录才查）
        if (currentUserId != null) {
            vo.setLiked(Boolean.TRUE.equals(
                    stringRedisTemplate.opsForSet().isMember(KEY_LIKE + id, currentUserId.toString())));
        }
        // 评论数
        vo.setCommentCount(Math.toIntExact(commentMapper.selectCount(
                new LambdaQueryWrapper<VideoComment>().eq(VideoComment::getVideoId, id))));
        return vo;
    }

    @Override
    public IPage<VideoVO> listMy(Long userId, long page, long size) {
        Page<Video> p = videoMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getUserId, userId)
                        .orderByDesc(Video::getCreateTime));
        return p.convert(this::toVO);
    }

    @Override
    public IPage<VideoVO> listPending(long page, long size, Long operatorId) {
        // 仅管理员可调用：与 audit 接口一致的权限模型
        if (!isAdmin(operatorId)) {
            throw new BusinessException("无权限：仅管理员可查看待审核视频");
        }
        // 按提交时间正序排：先提交的先审，避免新视频堆积在最后一页
        Page<Video> p = videoMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getStatus, VideoStatus.PENDING)
                        .orderByAsc(Video::getCreateTime));
        return p.convert(this::toVO);
    }

    @Override
    public void audit(Long id, int status, Long operatorId) {
        // 审核是平台运营动作，只有管理员（role=1）能操作，防止任意登录用户越权
        if (!isAdmin(operatorId)) {
            throw new BusinessException("无权限：仅管理员可审核视频");
        }
        Video video = videoMapper.selectById(id);
        if (video == null) {
            throw new BusinessException("视频不存在");
        }
        if (!video.getStatus().equals(VideoStatus.PENDING)) {
            throw new BusinessException("只有待审核的视频才能审核");
        }
        if (status != VideoStatus.PUBLISHED && status != VideoStatus.REJECTED) {
            throw new BusinessException("审核状态不合法");
        }
        Video update = new Video();
        update.setId(id);
        update.setStatus(status);
        videoMapper.updateById(update);
    }

    @Override
    public void offline(Long id, Long operatorId) {
        Video video = videoMapper.selectById(id);
        if (video == null) {
            throw new BusinessException("视频不存在");
        }
        requireOwnerOrAdmin(video, operatorId);
        if (!video.getStatus().equals(VideoStatus.PUBLISHED)) {
            throw new BusinessException("只有已发布的视频才能下架");
        }
        Video update = new Video();
        update.setId(id);
        update.setStatus(VideoStatus.OFFLINE);
        videoMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVideo(Long id, Long operatorId) {
        Video video = videoMapper.selectById(id);
        if (video == null) {
            throw new BusinessException("视频不存在");
        }
        requireOwnerOrAdmin(video, operatorId);
        // 级联清理互动数据：评论/收藏/观看历史/投币（避免留下孤儿记录）
        commentMapper.delete(new LambdaQueryWrapper<VideoComment>().eq(VideoComment::getVideoId, id));
        favoriteMapper.delete(new LambdaQueryWrapper<VideoFavorite>().eq(VideoFavorite::getVideoId, id));
        historyMapper.delete(new LambdaQueryWrapper<WatchHistory>().eq(WatchHistory::getVideoId, id));
        coinMapper.delete(new LambdaQueryWrapper<VideoCoin>().eq(VideoCoin::getVideoId, id));
        videoMapper.deleteById(id);
        // 清理 Redis 中的计数/点赞集合（否则残留 key 会让 detail 显示过期计数）
        stringRedisTemplate.delete(KEY_PLAY + id);
        stringRedisTemplate.delete(KEY_LIKE + id);
        stringRedisTemplate.delete(KEY_LIKE_COUNT + id);
    }

    // ==================== 模块2：播放计数 ====================

    @Override
    public void recordPlay(Long videoId, Long userId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null || !video.getStatus().equals(VideoStatus.PUBLISHED)) {
            throw new BusinessException("视频不存在或未发布");
        }
        // 1. 播放量：Redis 原子自增（高并发下 INCR 不会丢，定时任务再刷进 MySQL）
        stringRedisTemplate.opsForValue().increment(KEY_PLAY + videoId);

        // 2. 登录用户记录观看历史（同一视频只保留一条，重复看只更新时间）
        if (userId != null) {
            WatchHistory history = new WatchHistory();
            history.setVideoId(videoId);
            history.setUserId(userId);
            history.setWatchTime(LocalDateTime.now());
            try {
                historyMapper.insert(history);
            } catch (DuplicateKeyException e) {
                // 唯一约束冲突 = 之前看过，更新观看时间
                WatchHistory exist = historyMapper.selectOne(new LambdaQueryWrapper<WatchHistory>()
                        .eq(WatchHistory::getVideoId, videoId)
                        .eq(WatchHistory::getUserId, userId));
                if (exist != null) {
                    exist.setWatchTime(LocalDateTime.now());
                    historyMapper.updateById(exist);
                }
            }
        }
    }

    // ==================== 模块3：互动体系 ====================

    @Override
    public boolean toggleLike(Long videoId, Long userId) {
        requirePublished(videoId);
        // Redis Set 记录"谁点过赞"（去重 + 查状态快）
        String setKey = KEY_LIKE + videoId;
        String countKey = KEY_LIKE_COUNT + videoId;
        String uid = userId.toString();
        boolean liked = Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(setKey, uid));
        if (liked) {
            // 取消点赞：移出集合 + 计数 -1
            stringRedisTemplate.opsForSet().remove(setKey, uid);
            stringRedisTemplate.opsForValue().decrement(countKey);
        } else {
            // 点赞：加入集合 + 计数 +1
            stringRedisTemplate.opsForSet().add(setKey, uid);
            stringRedisTemplate.opsForValue().increment(countKey);
            // 通知视频作者（自己点赞自己的视频不通知）
            notifyOwner(videoId, userId, NotificationType.LIKE, null);
        }
        // 每次写操作都刷新集合过期时间，防止长期无人访问的 key 无限滞留（被删视频的 key 见 deleteVideo）
        stringRedisTemplate.expire(setKey, LIKE_SET_TTL);
        return !liked;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long videoId, Long userId) {
        requirePublished(videoId);
        // 收藏用数据库关系表（唯一约束天然防重复），计数直接更新库
        VideoFavorite exist = favoriteMapper.selectOne(new LambdaQueryWrapper<VideoFavorite>()
                .eq(VideoFavorite::getVideoId, videoId)
                .eq(VideoFavorite::getUserId, userId));
        if (exist == null) {
            VideoFavorite f = new VideoFavorite();
            f.setVideoId(videoId);
            f.setUserId(userId);
            favoriteMapper.insert(f);
            changeVideoCount(videoId, "favorite_count", 1);
            // 通知视频作者（自己收藏自己的视频不通知）
            notifyOwner(videoId, userId, NotificationType.FAVORITE, null);
            return true;
        } else {
            favoriteMapper.deleteById(exist.getId());
            changeVideoCount(videoId, "favorite_count", -1);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int coin(Long videoId, Long userId, int coin) {
        requirePublished(videoId);
        if (coin < 1 || coin > 2) {
            throw new BusinessException("单次投币数量为 1~2 枚");
        }
        // 每日限额：查今天已经投了多少
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Long todayCoins = coinMapper.selectCount(new LambdaQueryWrapper<VideoCoin>()
                .eq(VideoCoin::getUserId, userId)
                .ge(VideoCoin::getCreateTime, todayStart));
        if (todayCoins + coin > 3) {
            throw new BusinessException("今日投币已达上限（3 枚），明天再来吧");
        }
        // 记录投币 + 更新视频投币数
        VideoCoin record = new VideoCoin();
        record.setVideoId(videoId);
        record.setUserId(userId);
        record.setCoin(coin);
        coinMapper.insert(record);
        changeVideoCount(videoId, "coin_count", coin);
        // 通知视频作者（自己给自己视频投币不通知）
        notifyOwner(videoId, userId, NotificationType.COIN, null);
        return (int) (3 - todayCoins - coin);
    }

    @Override
    public IPage<CommentVO> comments(Long videoId, long page, long size) {
        // 1. 一级评论分页（parent_id = 0）
        Page<VideoComment> rootPage = commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<VideoComment>()
                        .eq(VideoComment::getVideoId, videoId)
                        .eq(VideoComment::getParentId, 0)
                        .orderByDesc(VideoComment::getCreateTime));
        List<VideoComment> roots = rootPage.getRecords();
        if (roots.isEmpty()) {
            return rootPage.convert(c -> toCommentVO(c, Collections.emptyList()));
        }

        // 2. 一次性查出所有一级评论下的回复（root_id in 一级id），避免逐条查询
        //    注意：一级评论自身的 root_id == 自己 id，会被 root_id in (...) 命中，
        //    因此必须额外加 parent_id <> 0 过滤，否则一级评论会作为"自己的回复"重复出现一次
        List<Long> rootIds = roots.stream().map(VideoComment::getId).collect(Collectors.toList());
        List<VideoComment> replies = commentMapper.selectList(new LambdaQueryWrapper<VideoComment>()
                .eq(VideoComment::getVideoId, videoId)
                .ne(VideoComment::getParentId, 0)
                .in(VideoComment::getRootId, rootIds)
                .orderByAsc(VideoComment::getCreateTime));

        // 3. 按 rootId 分组，组装到对应一级评论下
        Map<Long, List<VideoComment>> replyMap = replies.stream()
                .collect(Collectors.groupingBy(VideoComment::getRootId));
        return rootPage.convert(root -> toCommentVO(root,
                replyMap.getOrDefault(root.getId(), Collections.emptyList())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO addComment(Long videoId, Long userId, String content, Long parentId) {
        requirePublished(videoId);
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("评论内容不能为空");
        }
        if (content.length() > 500) {
            throw new BusinessException("评论不能超过 500 字");
        }

        long parent = parentId == null ? 0 : parentId;
        // 楼中楼结构说明：
        //   一级评论：parent_id=0，root_id=自己；
        //   回复：parent_id=被回复评论，root_id=其所属一级评论（若回复的是一级评论，root_id=它自己）
        VideoComment parentComment = null;
        if (parent != 0) {
            // 先校验"被回复的评论"存在且属于同一视频，再插入（避免插入后回滚的补偿写法）
            parentComment = commentMapper.selectById(parent);
            if (parentComment == null || !videoId.equals(parentComment.getVideoId())) {
                throw new BusinessException("被回复的评论不存在");
            }
        }

        VideoComment comment = new VideoComment();
        comment.setVideoId(videoId);
        comment.setUserId(userId);
        comment.setContent(content.trim());
        comment.setParentId(parent);
        commentMapper.insert(comment);

        if (parent == 0) {
            comment.setRootId(comment.getId());
            // 一级评论：通知视频作者（自己评论自己的视频不通知）
            notifyOwner(videoId, userId, NotificationType.COMMENT, content);
        } else {
            comment.setRootId(parentComment.getRootId() == 0 ? parentComment.getId() : parentComment.getRootId());
            // 楼中楼回复：通知被回复的人（自己回复自己不通知，notify 内部过滤）
            notificationService.notify(parentComment.getUserId(), userId, NotificationType.REPLY, videoId, content);
        }
        commentMapper.updateById(comment);

        // 重新查询，拿回数据库默认值（如 create_time）
        VideoComment saved = commentMapper.selectById(comment.getId());
        User user = userMapper.selectById(userId);
        return toCommentVO(saved, Collections.emptyList(), user);
    }

    // ==================== 模块5：个人中心 ====================

    @Override
    public IPage<VideoVO> myFavorites(Long userId, long page, long size) {
        Page<VideoFavorite> p = favoriteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<VideoFavorite>()
                        .eq(VideoFavorite::getUserId, userId)
                        .orderByDesc(VideoFavorite::getCreateTime));
        // 逐条联查视频：视频已被删除（作者删稿）的记录自动跳过，避免空指针/脏数据展示
        List<VideoVO> vos = new ArrayList<>();
        for (VideoFavorite f : p.getRecords()) {
            Video video = videoMapper.selectById(f.getVideoId());
            if (video == null) {
                continue;
            }
            VideoVO vo = toVO(video);
            vo.setInteractTime(f.getCreateTime());
            vos.add(vo);
        }
        Page<VideoVO> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(vos);
        return result;
    }

    @Override
    public IPage<VideoVO> myHistory(Long userId, long page, long size) {
        Page<WatchHistory> p = historyMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<WatchHistory>()
                        .eq(WatchHistory::getUserId, userId)
                        .orderByDesc(WatchHistory::getWatchTime));
        // 视频被删除的记录跳过（逻辑同上）
        List<VideoVO> vos = new ArrayList<>();
        for (WatchHistory h : p.getRecords()) {
            Video video = videoMapper.selectById(h.getVideoId());
            if (video == null) {
                continue;
            }
            VideoVO vo = toVO(video);
            vo.setInteractTime(h.getWatchTime());
            vos.add(vo);
        }
        Page<VideoVO> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(vos);
        return result;
    }

    // ==================== 私有工具方法 ====================

    /** 校验视频存在且已发布（评论/点赞/收藏/投币等互动的前提） */
    private void requirePublished(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null || !video.getStatus().equals(VideoStatus.PUBLISHED)) {
            throw new BusinessException("视频不存在或未发布");
        }
    }

    /** 校验操作者是管理员（role=1） */
    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userMapper.selectById(userId);
        return user != null && user.getRole() != null && user.getRole() == 1;
    }

    /** 校验操作者是视频作者或管理员（删除/下架等管理动作） */
    private void requireOwnerOrAdmin(Video video, Long operatorId) {
        boolean isOwner = operatorId != null && operatorId.equals(video.getUserId());
        if (!isOwner && !isAdmin(operatorId)) {
            throw new BusinessException("无权限操作该视频");
        }
    }

    /**
     * 互动通知视频作者：查视频归属，作者不是触发者本人时写通知
     * @param videoId 被互动的视频
     * @param actorId 触发互动的用户
     * @param type    通知类型（点赞/收藏/投币/评论）
     * @param extra   附加文本（评论内容，其他类型传 null）
     */
    private void notifyOwner(Long videoId, Long actorId, int type, String extra) {
        Video video = videoMapper.selectById(videoId);
        if (video != null) {
            notificationService.notify(video.getUserId(), actorId, type, videoId, extra);
        }
    }

    /**
     * 直接更新视频计数（收藏数/投币数）
     * 用 SQL 原子自增（count = count + n），避免"先查后改"在并发下互相覆盖
     * 播放/点赞走 Redis 增量不走这里（见模块2 与 CountSyncTask）
     * @param column 仅内部传入固定列名 favorite_count / coin_count，不会来自用户输入
     */
    private void changeVideoCount(Long videoId, String column, int delta) {
        videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .eq(Video::getId, videoId)
                .setSql("`" + column + "` = `" + column + "` + (" + delta + ")"));
    }

    /**
     * 实体 → VO：联查作者昵称，并合并 Redis 播放量增量
     * 合并逻辑与 detail 端点保持一致——这样列表(首页/收藏/历史/我的视频/搜索/feed)与详情页的播放数实时同步，
     * 不用等 5 分钟 CountSyncTask 落库，切换页面不会再出现"列表 0 / 详情 4"的错位
     */
    private VideoVO toVO(Video video) {
        if (video == null) {
            return null;
        }
        User author = userMapper.selectById(video.getUserId());
        VideoVO vo = VideoVO.from(video, author == null ? null : author.getNickname());
        // 播放量 = DB 字段 + Redis 未落库增量
        String playInc = stringRedisTemplate.opsForValue().get(KEY_PLAY + video.getId());
        if (playInc != null) {
            vo.setPlayCount(vo.getPlayCount() + Integer.parseInt(playInc));
        }
        return vo;
    }

    /** 评论实体列表 → VO（带昵称联查） */
    private CommentVO toCommentVO(VideoComment comment, List<VideoComment> replies) {
        User user = userMapper.selectById(comment.getUserId());
        return toCommentVO(comment, replies, user);
    }

    private CommentVO toCommentVO(VideoComment comment, List<VideoComment> replies, User user) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setVideoId(comment.getVideoId());
        vo.setUserId(comment.getUserId());
        vo.setNickname(user == null ? "匿名用户" : user.getNickname());
        vo.setContent(comment.getContent());
        vo.setParentId(comment.getParentId());
        vo.setRootId(comment.getRootId());
        vo.setCreateTime(comment.getCreateTime());
        // 回复列表：批量组装（N 个回复 × 查一次昵称会 N+1，练习场景可接受）
        vo.setReplies(replies.stream().map(r -> {
            CommentVO rv = new CommentVO();
            rv.setId(r.getId());
            rv.setVideoId(r.getVideoId());
            rv.setUserId(r.getUserId());
            User ru = userMapper.selectById(r.getUserId());
            rv.setNickname(ru == null ? "匿名用户" : ru.getNickname());
            rv.setContent(r.getContent());
            rv.setParentId(r.getParentId());
            rv.setRootId(r.getRootId());
            rv.setCreateTime(r.getCreateTime());
            return rv;
        }).collect(Collectors.toList()));
        return vo;
    }
}
