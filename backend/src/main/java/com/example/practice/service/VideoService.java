package com.example.practice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.practice.vo.CommentVO;
import com.example.practice.vo.VideoVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 视频服务接口：上传、查询、审核、播放计数、互动（点赞/收藏/投币/评论）、个人中心
 * userId 为 null 表示未登录（浏览公开视频）
 */
public interface VideoService {

    /**
     * 上传视频：文件存磁盘，记录入库（默认"待审核"状态）
     * @return 上传后的视频信息（含访问路径和状态）
     */
    VideoVO upload(Long userId, MultipartFile file, MultipartFile cover,
                   String title, String description, Integer category);

    /**
     * 分页查询已发布的视频（公开列表，供首页/分类页使用）
     * @param sort 排序：hot=按播放量，其他=按发布时间
     */
    IPage<VideoVO> list(long page, long size, Integer category, String sort);

    /**
     * 标题模糊搜索（公开接口，供搜索结果页使用）：只搜已发布的视频
     * @param sort 排序：hot=按播放量，其他=按发布时间
     */
    IPage<VideoVO> search(String keyword, long page, long size, String sort);

    /**
     * 关注动态流（需登录）：我关注的用户发布的视频，按发布时间倒序（拉模式 Feed）
     * 数据来源：user_follow 查我关注的人 → video 表按 user_id IN (...) 查已发布视频
     */
    IPage<VideoVO> feed(Long userId, long page, long size);

    /**
     * 查询某个用户已发布的视频（公开接口，他人主页用，不含待审核/驳回）
     */
    IPage<VideoVO> listByUser(Long userId, long page, long size);

    /**
     * 查询视频详情：已发布的所有人都能看；未发布（待审核/驳回/下架）只有作者本人能看
     * @param currentUserId 当前登录用户 ID，未登录传 null
     */
    VideoVO detail(Long id, Long currentUserId);

    /**
     * 分页查询"我的视频"（所有状态，个人中心用）
     */
    IPage<VideoVO> listMy(Long userId, long page, long size);

    /**
     * 管理员分页查询全站"待审核"视频（仅 role=1 可调）
     * @param operatorId 当前操作者 ID（内部校验是否为管理员）
     * @param page       页码（从 1 开始）
     * @param size       每页条数
     */
    IPage<VideoVO> listPending(long page, long size, Long operatorId);

    /**
     * 审核视频：只有"待审核"状态能审核，可改为"已发布"或"已驳回"
     * 权限：仅管理员（role=1）可审核，防止任意登录用户越权
     * @param operatorId 当前操作者 ID（内部校验是否为管理员）
     */
    void audit(Long id, int status, Long operatorId);

    /**
     * 下架视频（作者本人或管理员）：仅"已发布"状态可下架，下架后前台不可见
     * @param operatorId 当前操作者 ID
     */
    void offline(Long id, Long operatorId);

    /**
     * 删除视频（作者本人或管理员）：硬删除视频并级联清理其评论/收藏/历史/投币数据与 Redis 计数
     * @param operatorId 当前操作者 ID
     */
    void deleteVideo(Long id, Long operatorId);

    /**
     * 播放计数（Redis 增量，定时落库）+ 登录用户记录观看历史
     * @param userId 未登录传 null（不记历史，只计数）
     */
    void recordPlay(Long videoId, Long userId);

    /**
     * 点赞 / 取消点赞（Redis Set 去重）
     * @return 操作后的状态：true=已点赞，false=已取消
     */
    boolean toggleLike(Long videoId, Long userId);

    /**
     * 收藏 / 取消收藏（数据库唯一约束去重）
     * @return 操作后的状态：true=已收藏，false=已取消
     */
    boolean toggleFavorite(Long videoId, Long userId);

    /**
     * 投币：每人每天最多 3 枚
     * @return 今日剩余可投币数（0 表示已用完）
     */
    int coin(Long videoId, Long userId, int coin);

    /**
     * 分页查询视频的一级评论（每条带楼中楼回复 replies）
     */
    IPage<CommentVO> comments(Long videoId, long page, long size);

    /**
     * 发表评论（parentId=0 为一级评论，否则为楼中楼回复）
     * @return 完整评论信息
     */
    CommentVO addComment(Long videoId, Long userId, String content, Long parentId);

    /**
     * 分页查询"我的收藏"（个人中心用，按收藏时间倒序）
     */
    IPage<VideoVO> myFavorites(Long userId, long page, long size);

    /**
     * 分页查询"观看历史"（个人中心用，按观看时间倒序）
     */
    IPage<VideoVO> myHistory(Long userId, long page, long size);
}
