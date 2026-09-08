package com.example.practice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.practice.entity.VideoCoin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 投币记录数据访问接口
 */
@Mapper
public interface VideoCoinMapper extends BaseMapper<VideoCoin> {
}
