package com.example.practice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MQ 消息体：视频上传未带封面时，通知"异步 FFmpeg 抽帧"的消息内容
 * 生产者（上传接口）发这个对象，消费者（封面抽帧监听器）接收它
 * 封面是可选字段：即使抽帧失败/消息丢失，前端也有占位图兜底，不影响投稿主流程
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoverExtractMessage {

    /** 视频 id（消费者用它回写 cover_url） */
    private Long videoId;

    /** 已保存视频的对外访问路径，形如 /upload/yyyyMMdd/xxx.mp4（FFmpeg 抽帧输入） */
    private String videoUrl;
}
