package com.example.practice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.practice.entity.Video;
import org.apache.ibatis.annotations.Mapper;

/**
 * 视频数据访问接口：继承 BaseMapper 获得增删改查
 * 分页/条件查询直接用 LambdaQueryWrapper，不需要写 SQL
 */
@Mapper
public interface VideoMapper extends BaseMapper<Video> {
}
