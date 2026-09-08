package com.example.practice.constant;

/**
 * 视频状态常量：视频的"生命周期"状态机
 * 待审核 → 已发布 / 已驳回（驳回后可重新编辑再提交）
 * 已发布 → 已下架（作者或管理员操作）
 */
public final class VideoStatus {

    /** 待审核：上传后的默认状态，前台不可见，等后台审核 */
    public static final int PENDING = 0;

    /** 已发布：审核通过，前台可见、可播放 */
    public static final int PUBLISHED = 1;

    /** 已驳回：审核不通过，仅作者可见 */
    public static final int REJECTED = 2;

    /** 已下架：作者或管理员主动下架，前台不可见 */
    public static final int OFFLINE = 3;

    private VideoStatus() {
    }
}
