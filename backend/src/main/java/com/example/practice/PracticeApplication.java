package com.example.practice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类：项目入口
 * @EnableScheduling 开启定时任务（播放/点赞计数定时落库，见 CountSyncTask）
 */
@SpringBootApplication
@MapperScan("com.example.practice.mapper")
@EnableScheduling
public class PracticeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PracticeApplication.class, args);
    }
}
