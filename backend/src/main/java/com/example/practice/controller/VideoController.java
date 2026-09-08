package com.example.practice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.practice.common.Result;
import com.example.practice.dto.CommentDTO;
import com.example.practice.security.LoginUser;
import com.example.practice.service.VideoService;
import com.example.practice.vo.CommentVO;
import com.example.practice.vo.VideoVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 视频控制器
 * 接口鉴权规则（SecurityConfig 中配置）：
 * - 公开：GET /api/video/list（已发布列表）、GET /api/video/detail/{id}（详情，未发布仅作者可见）
 * - 需登录：POST /api/video/upload、GET /api/video/my、互动类接口
 * - 仅管理员：POST /api/video/{id}/audit（审核）
 * - 作者或管理员：DELETE /api/video/{id}（删除）、POST /api/video/{id}/offline（下架）
 */
@RestController
@RequestMapping("/api/video")
public class VideoController {

    @Autowired
    private VideoService videoService;

    /**
     * 上传视频（multipart/form-data）
     * 参数：file 视频文件（必填）、cover 封面（选填）、title、description、category
     */
    @PostMapping("/upload")
    public Result<VideoVO> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "cover", required = false) MultipartFile cover,
                                  @RequestParam("title") String title,
                                  @RequestParam(value = "description", required = false) String description,
                                  @RequestParam(value = "category", defaultValue = "0") Integer category) {
        return Result.success(videoService.upload(currentUserId(), file, cover, title, description, category));
    }

    /**
     * 分页查询已发布的视频（公开接口，首页/分类页用）
     * @param sort 排序：hot=按播放量（最热），其他=按发布时间（最新）
     */
    @GetMapping("/list")
    public Result<IPage<VideoVO>> list(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "12") long size,
                                       @RequestParam(required = false) Integer category,
                                       @RequestParam(required = false) String sort) {
        return Result.success(videoService.list(page, size, category, sort));
    }

    /**
     * 标题模糊搜索（公开接口，搜索结果页用）
     * @param sort 排序：hot=按播放量（最热），其他=按发布时间（最新）
     */
    @GetMapping("/search")
    public Result<IPage<VideoVO>> search(@RequestParam String keyword,
                                         @RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "12") long size,
                                         @RequestParam(required = false) String sort) {
        return Result.success(videoService.search(keyword, page, size, sort));
    }

    /**
     * 关注动态流（需登录）：我关注的用户发布的视频，按时间倒序
     */
    @GetMapping("/feed")
    public Result<IPage<VideoVO>> feed(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "12") long size) {
        return Result.success(videoService.feed(currentUserId(), page, size));
    }

    /**
     * 某个用户已发布的视频（公开接口，他人主页用，不含待审核/驳回）
     */
    @GetMapping("/user/{userId}")
    public Result<IPage<VideoVO>> listByUser(@PathVariable Long userId,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "12") long size) {
        return Result.success(videoService.listByUser(userId, page, size));
    }

    /**
     * 视频详情（公开接口）：已发布所有人可见，未发布仅作者本人可见
     * 用 /detail/{id} 而不是 /{id}，是为了和 /my 精确区分，方便 SecurityConfig 精确放行
     */
    @GetMapping("/detail/{id}")
    public Result<VideoVO> detail(@PathVariable Long id) {
        return Result.success(videoService.detail(id, currentUserIdOrNull()));
    }

    /**
     * 分页查询"我的视频"（需登录，个人中心用）
     */
    @GetMapping("/my")
    public Result<IPage<VideoVO>> my(@RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "12") long size) {
        return Result.success(videoService.listMy(currentUserId(), page, size));
    }

    /**
     * 管理员分页查询全站"待审核"视频（需登录且 role=1）
     * 用 /pending 而不是 /audit/pending，避免与 POST /{id}/audit 路径冲突
     */
    @GetMapping("/pending")
    public Result<IPage<VideoVO>> pending(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "20") long size) {
        return Result.success(videoService.listPending(page, size, currentUserId()));
    }

    /**
     * 审核视频（仅管理员）：把"待审核"改为"已发布(1)"或"已驳回(2)"
     * 权限在 service 层校验：操作者必须是 role=1 的管理员（测试用管理员：admin / 123456）
     */
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestParam Integer status) {
        videoService.audit(id, status, currentUserId());
        return Result.success();
    }

    /**
     * 下架视频（作者本人或管理员）：已发布 -> 已下架，前台不再展示
     */
    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        videoService.offline(id, currentUserId());
        return Result.success();
    }

    /**
     * 删除视频（作者本人或管理员）：硬删除并级联清理评论/收藏/历史/投币数据与 Redis 计数
     * 注意：删除不可恢复，前端删除前需二次确认
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteVideo(@PathVariable Long id) {
        videoService.deleteVideo(id, currentUserId());
        return Result.success();
    }

    /**
     * 播放计数（公开接口）：播放量 +1，登录用户同时记录观看历史
     * 前端进入播放页时调用一次即可
     */
    @PostMapping("/{id}/play")
    public Result<Void> play(@PathVariable Long id) {
        videoService.recordPlay(id, currentUserIdOrNull());
        return Result.success();
    }

    /**
     * 点赞 / 取消点赞（需登录）：点击切换，返回最新状态
     * @return true=已点赞，false=已取消
     */
    @PostMapping("/{id}/like")
    public Result<Boolean> like(@PathVariable Long id) {
        return Result.success(videoService.toggleLike(id, currentUserId()));
    }

    /**
     * 收藏 / 取消收藏（需登录）：点击切换，返回最新状态
     */
    @PostMapping("/{id}/favorite")
    public Result<Boolean> favorite(@PathVariable Long id) {
        return Result.success(videoService.toggleFavorite(id, currentUserId()));
    }

    /**
     * 投币（需登录）：每人每天最多 3 枚
     * @param coin 本次投币数量（1 或 2）
     * @return 今日剩余可投币数
     */
    @PostMapping("/{id}/coin")
    public Result<Integer> coin(@PathVariable Long id, @RequestParam(defaultValue = "1") int coin) {
        return Result.success(videoService.coin(id, currentUserId(), coin));
    }

    /**
     * 分页查询视频评论（公开接口）：一级评论分页，每条带楼中楼回复
     */
    @GetMapping("/{id}/comments")
    public Result<IPage<CommentVO>> comments(@PathVariable Long id,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size) {
        return Result.success(videoService.comments(id, page, size));
    }

    /**
     * 发表评论（需登录）：body 传 content 和 parentId（0=一级评论，非0=楼中楼回复）
     */
    @PostMapping("/{id}/comment")
    public Result<CommentVO> comment(@PathVariable Long id, @RequestBody CommentDTO dto) {
        return Result.success(videoService.addComment(id, currentUserId(), dto.getContent(), dto.getParentId()));
    }

    /**
     * 获取当前登录用户 ID（必须登录的接口用）
     */
    private Long currentUserId() {
        return currentUserIdOrNull();
    }

    /**
     * 获取当前登录用户 ID，未登录返回 null（公开接口用）
     * 登录后 JwtAuthenticationFilter 会把 LoginUser 放进 SecurityContext
     */
    private Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }
}
