package com.stock.platform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 文件存储服务
 * 用于处理头像等文件上传
 */
@Slf4j
@Service
public class FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${file.avatar-max-size:2097152}") // 默认2MB
    private long avatarMaxSize;

    /**
     * 上传头像
     *
     * @param file 上传的文件
     * @param userId 用户ID
     * @return 文件访问URL
     */
    public String uploadAvatar(MultipartFile file, Long userId) throws IOException {
        // 检查文件大小
        if (file.getSize() > avatarMaxSize) {
            throw new IllegalArgumentException("头像文件大小不能超过 " + (avatarMaxSize / 1024 / 1024) + "MB");
        }

        // 检查文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只能上传图片文件");
        }

        // 创建上传目录
        Path uploadPath = Paths.get(uploadDir, "avatars");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 生成文件名
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ? 
                originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
        String filename = "avatar_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

        // 保存文件
        Path targetLocation = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        log.info("头像上传Success: {}", filename);

        // 返回访问URL - 与 FileController 中的 @GetMapping("/uploads/avatars/{filename}") 匹配
        // 注意：前端请求时会自动加上 /api 前缀，所以这里只返回 /files/...
        return "/files/uploads/avatars/" + filename;
    }

    /**
     * 删除旧头像
     */
    public void deleteOldAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return;
        }

        try {
            // 从URL中提取文件名
            String filename = avatarUrl.substring(avatarUrl.lastIndexOf("/") + 1);
            Path filePath = Paths.get(uploadDir, "avatars", filename);
            Files.deleteIfExists(filePath);
            log.info("Delete old avatar: {}", filename);
        } catch (IOException e) {
            log.warn("Delete old avatarFailed: {}", e.getMessage());
        }
    }
}
