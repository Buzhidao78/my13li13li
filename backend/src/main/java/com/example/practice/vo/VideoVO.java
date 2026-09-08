package com.example.practice.vo;

import com.example.practice.entity.Video;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频出参 VO：给前端展示的数据
 * 在实体基础上补充了作者昵称（作者信息属于另一张表，查询时联查填充）
 */
@Data
public class VideoVO {

    private Long id;

    /** 作者用户 ID */
    private Long userId;

    /** 作者昵称（联查 user 表填充） */
    private String authorNickname;

    /** 视频标题 */
    private String title;

    /** 视频简介 */
    private String description;

    /** 视频文件访问路径（前端 <video> 直接用） */
    private String videoUrl;

    /** 封面访问路径 */
    private String coverUrl;

    /** 分类：0默认 1生活 2游戏 3科技 4美食 */
    private Integer category;

    /** 状态：0待审核 1已发布 2已驳回 3已下架 */
    private Integer status;

    /** 播放量 */
    private Integer playCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 收藏数 */
    private Integer favoriteCount;

    /** 投币数 */
    private Integer coinCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 当前用户是否已点赞（登录后有效，未登录为 null） */
    private Boolean liked;

    /** 评论数（详情接口返回） */
    private Integer commentCount;

    /** 互动时间（个人中心的收藏时间/观看时间用，其他场景为 null） */
    private LocalDateTime interactTime;

    /**
     * 实体 → VO：authorNickname 由 Service 联查后传入
     */
    public static VideoVO from(Video video, String authorNickname) {
        VideoVO vo = new VideoVO();
        vo.setId(video.getId());
        vo.setUserId(video.getUserId());
        vo.setAuthorNickname(authorNickname);
        vo.setTitle(video.getTitle());
        vo.setDescription(video.getDescription());
        vo.setVideoUrl(video.getVideoUrl());
        vo.setCoverUrl(video.getCoverUrl());
        vo.setCategory(video.getCategory());
        vo.setStatus(video.getStatus());
        vo.setPlayCount(video.getPlayCount());
        vo.setLikeCount(video.getLikeCount());
        vo.setFavoriteCount(video.getFavoriteCount());
        vo.setCoinCount(video.getCoinCount());
        vo.setCreateTime(video.getCreateTime());
        return vo;
    }
}
