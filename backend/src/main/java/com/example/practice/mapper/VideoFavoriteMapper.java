package com.example.practice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.practice.entity.VideoFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 视频收藏数据访问接口
 */
@Mapper
public interface VideoFavoriteMapper extends BaseMapper<VideoFavorite> {
}
