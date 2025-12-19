package com.example.admin.controller;

import com.example.admin.dto.ApiResponse;
import com.example.admin.dto.LoginRequest;
import com.example.admin.dto.LoginResponse;
import com.example.admin.service.SipService;
import com.example.admin.service.UserService;
import com.example.admin.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 处理登录、注销等认证相关请求
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private SipService sipService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            logger.info("收到登录请求: {}", request.getSipUri());

            // 验证参数
            if (request.getSipUri() == null || request.getSipUri().isEmpty()) {
                return ApiResponse.error("SIP URI 不能为空");
            }
            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                return ApiResponse.error("密码不能为空");
            }

            // 提取用户名（从 SIP URI 中提取用户名部分）
            String displayName = extractDisplayName(request.getSipUri());

            // 保存或更新用户到数据库
            try {
                userService.registerOrUpdate(displayName, request.getPassword(), displayName);
                logger.info("用户已保存到数据库: {}", displayName);
            } catch (Exception e) {
                logger.warn("保存用户到数据库失败: {}", e.getMessage());
            }

            // 生成 JWT Token
            String token = jwtUtil.generateToken(request.getSipUri(), displayName);

            // 构建响应
            LoginResponse response = new LoginResponse(
                    token,
                    request.getSipUri(),
                    displayName,
                    jwtUtil.getExpirationSeconds());

            logger.info("用户登录成功: {}", request.getSipUri());
            return ApiResponse.success(response, "登录成功");

        } catch (Exception e) {
            logger.error("登录失败: {}", request.getSipUri(), e);
            return ApiResponse.error("登录失败: " + e.getMessage());
        }
    }

    /**
     * 用户注销
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = extractToken(authHeader);
            if (token == null) {
                return ApiResponse.error("无效的 Authorization 头");
            }

            String userId = jwtUtil.getUserIdFromToken(token);

            // 服务端不再持有连接，所以也不需要在这里注销 SIP
            // sipService.unregister(userId);

            logger.info("用户注销成功: {}", userId);
            return ApiResponse.success(null, "注销成功");

        } catch (Exception e) {
            logger.error("注销失败", e);
            return ApiResponse.error("注销失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前用户信息
     * GET /api/auth/profile
     */
    @GetMapping("/profile")
    public ApiResponse<UserProfile> getProfile(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = extractToken(authHeader);
            if (token == null) {
                return ApiResponse.error("无效的 Authorization 头");
            }

            if (!jwtUtil.validateToken(token)) {
                return ApiResponse.error("Token 已过期或无效");
            }

            String userId = jwtUtil.getUserIdFromToken(token);
            String displayName = jwtUtil.getDisplayNameFromToken(token);

            // 状态改为查询数据库或默认为 ONLINE
            boolean registered = true;

            UserProfile profile = new UserProfile();
            profile.setUserId(userId);
            profile.setDisplayName(displayName);
            profile.setStatus(registered ? "ONLINE" : "OFFLINE");

            return ApiResponse.success(profile);

        } catch (Exception e) {
            logger.error("获取用户信息失败", e);
            return ApiResponse.error("获取用户信息失败: " + e.getMessage());
        }
    }

    /**
     * 更新用户状态
     * PUT /api/auth/status
     */
    @PutMapping("/status")
    public ApiResponse<Void> updateStatus(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody UpdateStatusRequest request) {
        try {
            String token = extractToken(authHeader);
            if (token == null) {
                return ApiResponse.error("无效的 Authorization 头");
            }

            String userId = jwtUtil.getUserIdFromToken(token);

            logger.info("用户 {} 更新状态为: {}", userId, request.getStatus());
            // TODO: 这里应该更新数据库中的状态

            return ApiResponse.success(null, "状态更新成功");

        } catch (Exception e) {
            logger.error("更新状态失败", e);
            return ApiResponse.error("更新状态失败: " + e.getMessage());
        }
    }

    /**
     * 从 Authorization 头中提取 Token
     */
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * 从 SIP URI 中提取显示名
     * 例如: sip:alice@192.168.1.100:5060 -> alice
     */
    private String extractDisplayName(String sipUri) {
        if (sipUri.startsWith("sip:")) {
            sipUri = sipUri.substring(4);
        }
        int atIndex = sipUri.indexOf('@');
        if (atIndex > 0) {
            return sipUri.substring(0, atIndex);
        }
        return sipUri;
    }

    // 内部类：用户资料
    public static class UserProfile {
        private String userId;
        private String displayName;
        private String status;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    // 内部类：更新状态请求
    public static class UpdateStatusRequest {
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}