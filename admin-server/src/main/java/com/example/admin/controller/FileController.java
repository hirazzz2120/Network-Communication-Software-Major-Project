package com.example.admin.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传下载控制器
 */
@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileController {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${server.port:8081}")
    private int serverPort;

    /**
     * 上传文件
     * POST /api/files/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "file") String type,
            @RequestParam(value = "sender", required = false) String sender) {

        try {
            // 确保上传目录存在
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 生成唯一文件名
            String originalFileName = file.getOriginalFilename();
            String extension = "";
            if (originalFileName != null && originalFileName.contains(".")) {
                extension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }
            String uniqueFileName = UUID.randomUUID().toString() + extension;

            // 保存文件
            Path filePath = uploadPath.resolve(uniqueFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // 构建下载URL
            String downloadUrl = String.format("http://10.29.133.174:%d/api/files/download/%s",
                    serverPort, uniqueFileName);
            // 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("fileName", originalFileName);
            result.put("savedName", uniqueFileName);
            result.put("fileSize", file.getSize());
            result.put("type", type);
            result.put("downloadUrl", downloadUrl);

            System.out.println("文件上传成功: " + originalFileName + " -> " + uniqueFileName);

            return ResponseEntity.ok(result);

        } catch (IOException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "文件上传失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 下载文件
     * GET /api/files/download/{fileName}
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                // 尝试确定文件类型
                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + fileName + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取文件信息
     * GET /api/files/info/{fileName}
     */
    @GetMapping("/info/{fileName}")
    public ResponseEntity<?> getFileInfo(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(fileName).normalize();

            if (Files.exists(filePath)) {
                Map<String, Object> info = new HashMap<>();
                info.put("fileName", fileName);
                info.put("fileSize", Files.size(filePath));
                info.put("contentType", Files.probeContentType(filePath));
                info.put("exists", true);
                return ResponseEntity.ok(info);
            } else {
                Map<String, Object> error = new HashMap<>();
                error.put("exists", false);
                error.put("error", "文件不存在");
                return ResponseEntity.notFound().build();
            }

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
