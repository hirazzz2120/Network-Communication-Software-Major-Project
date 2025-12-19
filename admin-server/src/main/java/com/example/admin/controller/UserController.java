package com.example.admin.controller;

import com.example.admin.entity.User;
import com.example.admin.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    // 🆕 新增：获取所有用户列表 (解决你访问报错的问题)
    // 浏览器访问 http://localhost:8081/api/users 就是调用这个
    @GetMapping
    public ResponseEntity<?> listUsers() {
        return ResponseEntity.ok(userService.listUsers());
    }

    // 注册接口
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> params) {
        try {
            User user = userService.register(
                    params.get("username"),
                    params.get("password"),
                    params.get("nickname")
            );
            return ResponseEntity.ok("注册成功，ID: " + user.getId());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 登录接口
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> params) {
        User user = userService.login(
                params.get("username"),
                params.get("password")
        );
        if (user != null) {
            return ResponseEntity.ok(user);
        }
        return ResponseEntity.status(401).body("登录失败");
    }
}