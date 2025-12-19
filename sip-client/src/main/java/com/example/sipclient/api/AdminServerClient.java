package com.example.sipclient.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Admin Server 客户端
 * 用于与后端业务服务器通信，同步用户、消息、通话记录
 */
public class AdminServerClient {

    private static final String DEFAULT_SERVER_URL = "http://localhost:8081";
    private String serverUrl;
    private String authToken;

    public AdminServerClient() {
        this.serverUrl = DEFAULT_SERVER_URL;
    }

    public AdminServerClient(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    /**
     * 用户登录同步
     */
    public boolean syncUserLogin(String sipUri, String password) {
        try {
            String json = String.format(
                    "{\"sipUri\":\"%s\",\"password\":\"%s\",\"localIp\":\"127.0.0.1\",\"localPort\":5061}",
                    escapeJson(sipUri), escapeJson(password));

            String response = post("/api/auth/login", json);
            if (response != null && response.contains("\"success\":true")) {
                // 提取 token
                int tokenStart = response.indexOf("\"token\":\"") + 9;
                int tokenEnd = response.indexOf("\"", tokenStart);
                if (tokenStart > 8 && tokenEnd > tokenStart) {
                    this.authToken = response.substring(tokenStart, tokenEnd);
                }
                System.out.println("[AdminServerClient] 用户登录已同步到服务器");
                return true;
            } else if (response != null) {
                System.out.println(
                        "[AdminServerClient] 服务器响应: " + response.substring(0, Math.min(100, response.length())));
                return true; // 即使 token 提取失败，只要有响应就算成功
            }
        } catch (Exception e) {
            System.err.println("[AdminServerClient] 登录同步失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 发送消息记录
     */
    public boolean recordMessage(String sender, String receiver, String content) {
        try {
            String json = String.format(
                    "{\"sender\":\"%s\",\"receiver\":\"%s\",\"content\":\"%s\"}",
                    escapeJson(sender), escapeJson(receiver), escapeJson(content));

            String response = post("/api/messages/send", json);
            if (response != null) {
                System.out.println("[AdminServerClient] 消息已记录到服务器");
                return true;
            }
        } catch (Exception e) {
            System.err.println("[AdminServerClient] 消息记录失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 记录通话
     */
    public boolean recordCall(String caller, String callee, long durationSeconds, String type) {
        try {
            String json = String.format(
                    "{\"caller\":\"%s\",\"callee\":\"%s\",\"duration\":%d,\"type\":\"%s\"}",
                    escapeJson(caller), escapeJson(callee), durationSeconds, type);

            String response = post("/api/calls/save", json);
            if (response != null) {
                System.out.println("[AdminServerClient] 通话记录已保存到服务器");
                return true;
            }
        } catch (Exception e) {
            System.err.println("[AdminServerClient] 通话记录保存失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 用户离线同步
     */
    public boolean syncUserLogout(String username) {
        try {
            String json = String.format("{\"username\":\"%s\"}", escapeJson(username));
            String response = post("/api/users/offline", json);
            if (response != null) {
                System.out.println("[AdminServerClient] 用户已设为离线: " + username);
                return true;
            }
        } catch (Exception e) {
            System.err.println("[AdminServerClient] 离线同步失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 检查服务器是否可用
     */
    public boolean isServerAvailable() {
        try {
            URL url = new URL(serverUrl + "/api/auth/profile");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int code = conn.getResponseCode();
            return code >= 200 && code < 500; // 服务器响应即可
        } catch (Exception e) {
            return false;
        }
    }

    private String post(String path, String jsonBody) throws Exception {
        URL url = new URL(serverUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        }
        return null;
    }

    private String escapeJson(String str) {
        if (str == null)
            return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
