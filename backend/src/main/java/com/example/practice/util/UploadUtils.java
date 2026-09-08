package com.example.practice.util;

import com.example.practice.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 文件上传工具类：统一处理"校验 + 存储 + 返回访问路径"
 * 设计要点：
 * 1. 白名单校验：只允许指定的视频/图片类型（防止上传恶意文件）
 * 2. 大小校验：视频上限 100MB，封面上限 5MB
 * 3. 按日期分目录 + UUID 文件名：避免重名覆盖，也方便按时间管理
 * 4. 存储路径与访问路径分离：磁盘存 ./upload/20260907/xxx.mp4，对外访问 /upload/20260907/xxx.mp4
 */
@Slf4j
@Component
public class UploadUtils {

    /** 上传根目录：从 application.yml 的 file.upload-dir 读取，默认 ./upload */
    @Value("${file.upload-dir:./upload}")
    private String uploadDir;

    /** 允许的视频扩展名（白名单） */
    private static final List<String> VIDEO_EXTS = List.of("mp4", "webm", "mov", "avi");

    /** 允许的图片扩展名（白名单，封面用） */
    private static final List<String> IMAGE_EXTS = List.of("jpg", "jpeg", "png", "webp", "gif");

    /** 视频大小上限：100MB */
    private static final long MAX_VIDEO_SIZE = 100L * 1024 * 1024;

    /** 图片大小上限：5MB */
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    /**
     * 保存视频文件，返回对外访问路径（如 /upload/20260907/xxx.mp4）
     */
    public String saveVideo(MultipartFile file) {
        return save(file, VIDEO_EXTS, MAX_VIDEO_SIZE, "video/");
    }

    /**
     * 保存封面图片，返回对外访问路径
     */
    public String saveImage(MultipartFile file) {
        return save(file, IMAGE_EXTS, MAX_IMAGE_SIZE, "image/");
    }

    /**
     * 通用保存逻辑：校验（扩展名白名单 + 大小 + Content-Type）→ 生成路径 → 写入磁盘 → 返回访问路径
     * @param expectedContentTypePrefix Content-Type 前缀（video/ 或 image/），防扩展名伪装
     */
    private String save(MultipartFile file, List<String> allowExts, long maxSize, String expectedContentTypePrefix) {
        // 1. 空文件校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }

        // 2. 扩展名校验：从原始文件名里取扩展名，必须命中白名单
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = StringUtils.getFilenameExtension(originalName);
        if (ext == null || !allowExts.contains(ext.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("不支持的文件类型：" + originalName);
        }

        // 2.1 Content-Type 兜底校验：浏览器伪造扩展名时（如 .mp4 实际是 html），拒绝上传
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith(expectedContentTypePrefix)) {
            throw new BusinessException("文件内容类型不合法，请上传正确的 " + expectedContentTypePrefix + " 文件");
        }

        // 3. 大小校验
        if (file.getSize() > maxSize) {
            throw new BusinessException("文件大小超过限制（最大 " + (maxSize / 1024 / 1024) + "MB）");
        }

        // 4. 生成存储目录：{uploadDir}/{yyyyMMdd}/，目录不存在就创建
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Path dir = Paths.get(uploadDir, dateDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessException("创建上传目录失败");
        }

        // 5. 生成唯一文件名：UUID + 扩展名（避免重名覆盖）
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase(Locale.ROOT);

        // 6. 写入磁盘：用 Files.copy 读输入流写目标文件（比 transferTo 更可控、跨容器行为一致）
        try {
            Path target = dir.resolve(fileName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("文件保存失败，原始文件名: {}", originalName, e);
            throw new BusinessException("文件保存失败，请重试");
        }

        // 7. 返回对外访问路径（WebConfig 里已把 /upload/** 映射到这个磁盘目录）
        return "/upload/" + dateDir + "/" + fileName;
    }

    /**
     * 删除已保存的上传文件（回滚清理用）
     * @param fileUrl 对外访问路径，形如 /upload/20260907/xxx.mp4；空值直接忽略
     */
    public void deleteIfExists(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }
        try {
            // 访问路径 /upload/xxx -> 磁盘路径 {uploadDir}/xxx
            String relative = fileUrl.startsWith("/upload/")
                    ? fileUrl.substring("/upload/".length())
                    : fileUrl;
            Files.deleteIfExists(Paths.get(uploadDir, relative));
        } catch (IOException e) {
            log.warn("清理上传文件失败: {}", fileUrl, e);
        }
    }
}
