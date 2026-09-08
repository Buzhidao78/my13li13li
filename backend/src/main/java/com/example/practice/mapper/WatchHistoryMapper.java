package com.example.practice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.practice.entity.WatchHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 观看历史数据访问接口
 */
@Mapper
public interface WatchHistoryMapper extends BaseMapper<WatchHistory> {
}
