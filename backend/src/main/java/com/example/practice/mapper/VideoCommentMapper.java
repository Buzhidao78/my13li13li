package com.example.practice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.practice.entity.VideoComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 视频评论数据访问接口
 */
@Mapper
public interface VideoCommentMapper extends BaseMapper<VideoComment> {
}
