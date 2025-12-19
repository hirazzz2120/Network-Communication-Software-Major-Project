package com.example.sipclient.gui.controller;

import com.example.sipclient.api.AdminServerClient;
import com.example.sipclient.sip.SipUserAgent;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.time.Duration;

/**
 * 登录界面控制器
 */
public class LoginController {

    @FXML
    private TextField sipUriField;
    @FXML
    private TextField serverUrlField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField localIpField;
    @FXML
    private TextField localPortField;
    @FXML
    private Button loginButton;
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressIndicator progressIndicator;

    private SipUserAgent userAgent;
    private static SipUserAgent globalUserAgent; // 静态引用用于shutdown hook
    private static AdminServerClient adminClient = new AdminServerClient(); // Admin Server 客户端

    @FXML
    public void initialize() {
        // 设置默认值
        sipUriField.setText("sip:101@10.29.133.174:5060");
        serverUrlField.setText("http://10.29.133.174:8081");
        passwordField.setText("101");
        localIpField.setText("10.29.133.174");
        localPortField.setText("5061");

        progressIndicator.setVisible(false);
    }

    @FXML
    private void handleLogin() {
        String sipUri = sipUriField.getText().trim();
        String serverUrl = serverUrlField.getText().trim();
        String password = passwordField.getText();
        String localIp = localIpField.getText().trim();
        String localPortStr = localPortField.getText().trim();

        // 验证输入
        if (sipUri.isEmpty() || serverUrl.isEmpty() || password.isEmpty() || localIp.isEmpty() || localPortStr.isEmpty()) {
            showError("请填写所有字段");
            return;
        }
        if (!serverUrl.startsWith("http")) {
            showError("业务服务器地址格式错误");
            return;
        }

        int localPort;
        try {
            localPort = Integer.parseInt(localPortStr);
            if (localPort < 1024 || localPort > 65535) {
                showError("端口号必须在 1024-65535 之间");
                return;
            }
        } catch (NumberFormatException e) {
            showError("端口号格式错误");
            return;
        }

        adminClient.setServerUrl(serverUrl);

        // 禁用登录按钮，显示进度
        loginButton.setDisable(true);
        progressIndicator.setVisible(true);
        statusLabel.setText("正在连接...");
        statusLabel.setStyle("-fx-text-fill: #666;");

        // 异步登录
        new Thread(() -> {
            try {
                userAgent = new SipUserAgent(sipUri, password, localIp, localPort);
                globalUserAgent = userAgent; // 保存静态引用

                // 注册 JVM 关闭钩子
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println("[ShutdownHook] JVM 正在关闭，强制清理 SIP 连接...");
                    if (globalUserAgent != null) {
                        try {
                            globalUserAgent.shutdown();
                            System.out.println("[ShutdownHook] SIP 连接已关闭");
                        } catch (Exception e) {
                            System.err.println("[ShutdownHook] 关闭失败: " + e.getMessage());
                        }
                    }
                }, "SIP-Cleanup-Hook"));

                boolean success = userAgent.register(Duration.ofSeconds(10));

                Platform.runLater(() -> {
                    if (success) {
                        // 同步登录到 admin-server（后台异步，不阻塞主流程）
                        new Thread(() -> {
                            adminClient.syncUserLogin(sipUri, password);
                        }).start();

                        openMainWindow();
                    } else {
                        showError("注册失败，请检查配置");
                        loginButton.setDisable(false);
                        progressIndicator.setVisible(false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("连接失败: " + e.getMessage());
                    loginButton.setDisable(false);
                    progressIndicator.setVisible(false);
                });
            }
        }).start();
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: #d32f2f;");
    }

    private void openMainWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            Scene scene = new Scene(loader.load());

            MainController controller = loader.getController();
            controller.setUserAgent(userAgent);
            controller.setAdminClient(adminClient); // 传递 AdminServerClient
            controller.setCurrentUserSipUri(sipUriField.getText().trim()); // 传递当前用户 SIP URI
            controller.setServerUrl(serverUrlField.getText().trim()); // 传递业务服务器地址

            // 保存当前 userAgent 到 scene
            scene.setUserData(this);

            Stage stage = (Stage) loginButton.getScene().getWindow();

            // 添加窗口关闭事件
            stage.setOnCloseRequest(event -> {
                cleanup();
            });

            stage.setScene(scene);
            stage.setTitle("SIP 客户端 - " + sipUriField.getText());
            stage.setResizable(true);
            stage.setMinWidth(900);
            stage.setMinHeight(600);
            stage.centerOnScreen();
        } catch (Exception e) {
            e.printStackTrace();
            showError("打开主界面失败: " + e.getMessage());
        }
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        System.out.println("[LoginController] 开始清理资源...");

        // 同步用户离线状态到 admin-server
        try {
            String username = extractUsername(sipUriField.getText());
            if (username != null && !username.isEmpty()) {
                adminClient.syncUserLogout(username);
            }
        } catch (Exception e) {
            System.err.println("[LoginController] 离线状态同步失败: " + e.getMessage());
        }

        if (userAgent != null) {
            try {
                System.out.println("[LoginController] 正在关闭 SIP 连接（包括注销）...");
                userAgent.shutdown();
                Thread.sleep(200); // 等待注销消息发送和端口释放
                userAgent = null;
                globalUserAgent = null;
                System.out.println("[LoginController] SIP 连接已关闭，资源已释放");
            } catch (Exception e) {
                System.err.println("[LoginController] 关闭 SIP 连接失败: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("[LoginController] 没有需要清理的 SIP 连接");
        }
    }

    /**
     * 从 SIP URI 中提取用户名
     */
    private String extractUsername(String sipUri) {
        if (sipUri == null)
            return null;
        if (sipUri.startsWith("sip:")) {
            sipUri = sipUri.substring(4);
        }
        int atIndex = sipUri.indexOf('@');
        if (atIndex > 0) {
            return sipUri.substring(0, atIndex);
        }
        return sipUri;
    }
}
