package com.example.practice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.practice.entity.VideoCoin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 投币记录数据访问接口
 */
@Mapper
public interface VideoCoinMapper extends BaseMapper<VideoCoin> {

    /**
     * 查询用户某时间点之后投出的总枚数（SUM 而非 COUNT，因为单条记录可含 1~2 枚）
     * 用于 Redis 计数 key 丢失时以数据库存量为准重建当日额度
     */
    @Select("SELECT COALESCE(SUM(coin), 0) FROM video_coin WHERE user_id = #{userId} AND create_time >= #{startTime}")
    Long selectTotalCoinsSince(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime);
}
