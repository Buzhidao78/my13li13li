package com.example.practice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：把上传目录映射成静态资源
 * 作用：/upload/20260907/xxx.mp4 这类路径能被浏览器直接访问
 * 原理：用户访问 /upload/** 时，Spring 去磁盘的 {file.upload-dir}/ 目录下找对应文件
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 上传根目录（与 UploadUtils 里读同一个配置） */
    @Value("${file.upload-dir:./upload}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 注意：addResourceLocations 的路径必须以 / 结尾
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
