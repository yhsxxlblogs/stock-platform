package com.stock.platform.controller;

import com.stock.platform.dto.ApiResponse;
import com.stock.platform.entity.User;
import com.stock.platform.repository.UserRepository;
import com.stock.platform.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件上传控制器
 */
@Slf4j
@RestController
@RequestMapping("/files")
public class FileController {

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private UserRepository userRepository;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    /**
     * 上传头像
     */
    @PostMapping("/avatar")
    public ResponseEntity<ApiResponse<String>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        
        try {
            // 获取当前用户名
            String username = authentication.getName();
            // 从数据库获取用户ID
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            Long userId = user.getId();
            
            // 保存旧头像URL用于删除
            String oldAvatarUrl = user.getAvatar();
            
            // 上传文件
            String fileUrl = fileStorageService.uploadAvatar(file, userId);
            
            // 删除旧头像文件（如果存在且不是默认头像）
            if (oldAvatarUrl != null && !oldAvatarUrl.isEmpty() && !oldAvatarUrl.startsWith("http")) {
                fileStorageService.deleteOldAvatar(oldAvatarUrl);
            }
            
            return ResponseEntity.ok(ApiResponse.success("头像上传成功", fileUrl));
        } catch (IllegalArgumentException e) {
            log.warn("头像上传Failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (IOException e) {
            log.error("头像上传Failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "头像上传失败: " + e.getMessage()));
        }
    }

    /**
     * 获取上传的文件
     */
    @GetMapping("/uploads/avatars/{filename}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        try {
            // 使用与FileStorageService相同的路径构建方式
            Path filePath = Paths.get(uploadDir, "avatars").resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            
            log.info("尝试获取头像: {}, 完整路径: {}", filename, filePath.toAbsolutePath());
            
            if (resource.exists()) {
                log.info("头像文件存在: {}", filename);
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                        .body(resource);
            } else {
                log.warn("头像文件不存在: {}, 路径: {}", filename, filePath.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            log.error("Get头像Failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
