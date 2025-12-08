package com.example.sipclient.gui.controller;

import com.example.sipclient.call.CallManager;
import com.example.sipclient.chat.MessageHandler;
import com.example.sipclient.gui.model.Contact;
import com.example.sipclient.gui.model.Message;
import com.example.sipclient.gui.storage.LocalDatabase;
import com.example.sipclient.sip.SipUserAgent;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 主界面控制器
 */
public class MainController {

    @FXML private ListView<Contact> contactListView;
    @FXML private VBox chatBox;
    @FXML private ScrollPane chatScrollPane;
    @FXML private TextArea messageInput;
    @FXML private Button sendButton;
    @FXML private Button callButton;
    @FXML private Button videoCallButton;
    @FXML private Label chatTitleLabel;
    @FXML private Label statusLabel;
    @FXML private TextField searchField;

    private SipUserAgent userAgent;
    private CallManager callManager;
    private Contact currentContact;
    private ObservableList<Contact> contacts;
    private ObservableList<Contact> allContacts;
    private LocalDatabase database;

    @FXML
    public void initialize() {
        // 初始化数据库
        database = new LocalDatabase();
        database.initialize();
        
        // 初始化联系人列表
        allContacts = FXCollections.observableArrayList();
        contacts = FXCollections.observableArrayList();
        contactListView.setItems(contacts);
        contactListView.setCellFactory(lv -> new ContactCell());
        
        // 从数据库加载联系人
        loadContactsFromDatabase();
        
        // 如果没有联系人，添加默认测试联系人
        if (allContacts.isEmpty()) {
            allContacts.add(new Contact("102", "sip:102@10.29.133.174:5060", "用户 102"));
            allContacts.add(new Contact("111", "sip:111@10.29.133.174:5060", "用户 111"));
            allContacts.add(new Contact("103", "sip:103@10.29.133.174:5060", "用户 103"));
            
            // 保存到数据库
            for (Contact contact : allContacts) {
                database.saveContact(contact);
            }
        }
        
        contacts.addAll(allContacts);
        
        // 搜索功能
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterContacts(newVal));
        
        // 监听联系人选择
        contactListView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> selectContact(newVal)
        );
        
        // 添加右键菜单
        setupContextMenu();
        
        // 禁用聊天控件直到选择联系人
        messageInput.setDisable(true);
        sendButton.setDisable(true);
        callButton.setDisable(true);
        videoCallButton.setDisable(true);
        
        // 回车发送
        messageInput.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER") && !event.isShiftDown()) {
                event.consume();
                handleSendMessage();
            }
        });
        
        statusLabel.setText("就绪");
    }

    public void setUserAgent(SipUserAgent userAgent) {
        this.userAgent = userAgent;
        
        // 初始化 CallManager
        callManager = new CallManager();
        userAgent.setCallManager(callManager);
        
        // 设置消息处理器
        userAgent.setMessageHandler(new MessageHandler() {
            @Override
            public void handleIncomingMessage(String from, String body) {
                Platform.runLater(() -> {
                    // 查找或创建联系人
                    Contact contact = findContactByUri(from);
                    if (contact == null) {
                        contact = new Contact(extractUserId(from), from, "用户 " + extractUserId(from));
                        allContacts.add(contact);
                        contacts.add(contact);
                        // 保存新联系人到数据库
                        database.saveContact(contact);
                    }
                    
                    // 添加消息到联系人
                    Message msg = new Message(body, false, LocalDateTime.now());
                    contact.getMessages().add(msg);
                    contact.setLastMessage(body);
                    contact.setLastMessageTime(LocalDateTime.now());
                    
                    // 保存到数据库
                    if (SettingsController.isHistorySaveEnabled()) {
                        database.saveMessage(contact.getUserId(), msg);
                        database.saveContact(contact);
                    }
                    
                    // 如果是当前聊天对象，显示消息
                    if (contact.equals(currentContact)) {
                        displayMessage(msg);
                    } else {
                        // 增加未读计数
                        contact.incrementUnreadCount();
                    }
                    
                    // 刷新列表
                    contactListView.refresh();
                });
            }
        });
        
        // 设置来电监听器
        callManager.setIncomingCallListener((fromUri, sessionId) -> {
            Platform.runLater(() -> showIncomingCallDialog(fromUri, sessionId));
        });
        
        statusLabel.setText("已连接");
    }

    private void selectContact(Contact contact) {
        if (contact == null) return;
        
        currentContact = contact;
        chatTitleLabel.setText(contact.getDisplayName());
        
        // 启用聊天控件
        messageInput.setDisable(false);
        sendButton.setDisable(false);
        callButton.setDisable(false);
        videoCallButton.setDisable(false);
        
        // 清空聊天窗口
        chatBox.getChildren().clear();
        
        // 从数据库加载历史消息
        if (SettingsController.isHistorySaveEnabled()) {
            List<Message> history = database.loadMessages(contact.getUserId());
            contact.getMessages().addAll(history);
        }
        
        // 显示历史消息
        for (Message msg : contact.getMessages()) {
            displayMessage(msg);
        }
        
        // 清除未读计数
        contact.clearUnreadCount();
        contactListView.refresh();
        
        // 聚焦输入框
        messageInput.requestFocus();
    }

    @FXML
    private void handleSendMessage() {
        if (currentContact == null) return;
        
        String text = messageInput.getText().trim();
        if (text.isEmpty()) return;
        
        try {
            userAgent.sendMessage(currentContact.getSipUri(), text);
            
            // 添加到聊天记录
            Message msg = new Message(text, true, LocalDateTime.now());
            currentContact.getMessages().add(msg);
            currentContact.setLastMessage(text);
            currentContact.setLastMessageTime(LocalDateTime.now());
            
            // 保存到数据库
            if (SettingsController.isHistorySaveEnabled()) {
                database.saveMessage(currentContact.getUserId(), msg);
                database.saveContact(currentContact);
            }
            
            displayMessage(msg);
            messageInput.clear();
            contactListView.refresh();
            
        } catch (Exception e) {
            showAlert("发送失败", "无法发送消息: " + e.getMessage());
        }
    }

    @FXML
    private void handleMakeCall() {
        if (currentContact == null) return;
        
        try {
            userAgent.makeCall(currentContact.getSipUri());
            statusLabel.setText("呼叫中: " + currentContact.getDisplayName());
            
            // 打开通话窗口
            showCallWindow(currentContact);
            
        } catch (Exception e) {
            showAlert("呼叫失败", "无法发起呼叫: " + e.getMessage());
        }
    }

    @FXML
    private void handleMakeVideoCall() {
        // 1. 检查是否选择了联系人
        if (currentContact == null) {
            showAlert("提示", "请先选择一个联系人");
            return;
        }

        try {
            // 2. 调用 SipUserAgent 发起呼叫，第二个参数 true 表示【开启视频】
            // 这就是连接点！它会告诉后端：“我要打视频电话，请把视频引擎 VideoSession 开起来”
            userAgent.startCall(currentContact.getSipUri(), true);

            // 3. 更新界面状态
            statusLabel.setText("正在发起视频呼叫: " + currentContact.getDisplayName());

            // 4. 打开通话窗口 (显示对方画面)
            showCallWindow(currentContact);

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("呼叫失败", "无法发起视频呼叫: " + e.getMessage());
        }
    }
    private void displayMessage(Message msg) {
        HBox messageBox = new HBox(10);
        messageBox.setAlignment(msg.isFromMe() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        
        Label messageLabel = new Label(msg.getContent());
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(400);
        messageLabel.setStyle(msg.isFromMe() 
            ? "-fx-background-color: #0084ff; -fx-text-fill: white; -fx-padding: 10; -fx-background-radius: 15;"
            : "-fx-background-color: #e4e6eb; -fx-text-fill: black; -fx-padding: 10; -fx-background-radius: 15;");
        
        VBox msgContainer = new VBox(5);
        msgContainer.setAlignment(msg.isFromMe() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        
        Label timeLabel = new Label(msg.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")));
        timeLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 11px;");
        
        msgContainer.getChildren().addAll(messageLabel, timeLabel);
        messageBox.getChildren().add(msgContainer);
        
        chatBox.getChildren().add(messageBox);
        
        // 滚动到底部
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    private void showIncomingCallDialog(String fromUri, String sessionId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/incoming_call.fxml"));
            Scene scene = new Scene(loader.load());
            
            IncomingCallController controller = loader.getController();
            controller.setCallInfo(fromUri, sessionId, userAgent);
            
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.setTitle("来电");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(false);
            stage.show();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showCallWindow(Contact contact) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/call.fxml"));
            Scene scene = new Scene(loader.load());
            
            CallController controller = loader.getController();
            controller.setCallInfo(contact, userAgent, callManager, false); // false表示是发起方
            
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.setTitle("通话中 - " + contact.getDisplayName());
            stage.setResizable(false);
            stage.show();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Contact findContactByUri(String uri) {
        return contacts.stream()
            .filter(c -> c.getSipUri().equals(uri) || uri.contains(c.getUserId()))
            .findFirst()
            .orElse(null);
    }

    private String extractUserId(String uri) {
        if (uri.contains("@")) {
            String part = uri.substring(uri.indexOf(":") + 1, uri.indexOf("@"));
            return part;
        }
        return uri;
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * 过滤联系人
     */
    private void filterContacts(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            contacts.setAll(allContacts);
        } else {
            contacts.clear();
            String lowerSearch = searchText.toLowerCase();
            for (Contact contact : allContacts) {
                if (contact.getDisplayName().toLowerCase().contains(lowerSearch) ||
                    contact.getUserId().toLowerCase().contains(lowerSearch) ||
                    (contact.getLastMessage() != null && contact.getLastMessage().toLowerCase().contains(lowerSearch))) {
                    contacts.add(contact);
                }
            }
        }
    }

    /**
     * 添加联系人
     */
    @FXML
    private void handleAddContact() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("添加联系人");
        dialog.setHeaderText("添加新联系人");
        dialog.setContentText("请输入用户 ID (如: 103):");
        
        dialog.showAndWait().ifPresent(userId -> {
            if (!userId.trim().isEmpty()) {
                String sipUri = "sip:" + userId + "@10.29.133.174:5060";
                Contact newContact = new Contact(userId, sipUri, "用户 " + userId);
                
                // 检查是否已存在
                boolean exists = allContacts.stream()
                    .anyMatch(c -> c.getUserId().equals(userId));
                
                if (!exists) {
                    allContacts.add(newContact);
                    contacts.add(newContact);
                    showInfoAlert("添加成功", "联系人已添加");
                } else {
                    showInfoAlert("提示", "该联系人已存在");
                }
            }
        });
    }

    /**
     * 打开设置
     */
    @FXML
    private void handleOpenSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings.fxml"));
            Scene scene = new Scene(loader.load());
            
            SettingsController controller = loader.getController();
            Stage stage = new Stage();
            controller.setStage(stage);
            controller.setOnSettingsChanged(() -> applySettings());
            
            stage.setScene(scene);
            stage.setTitle("设置");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(false);
            stage.show();
            
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("错误", "无法打开设置界面");
        }
    }

    /**
     * 注销登录
     */
    @FXML
    private void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认注销");
        alert.setHeaderText("确定要注销登录吗？");
        alert.setContentText("这将断开当前连接并返回到登录界面。");
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    // 断开 SIP 连接
                    if (userAgent != null) {
                        try {
                            System.out.println("[MainController] 正在关闭 SIP 连接...");
                            userAgent.shutdown();
                            System.out.println("[MainController] SIP 连接已关闭");
                        } catch (Exception e) {
                            System.err.println("关闭 SIP 连接失败: " + e.getMessage());
                        }
                    }
                    
                    // 关闭当前窗口
                    Stage stage = (Stage) contactListView.getScene().getWindow();
                    
                    // 打开登录界面
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
                    Scene scene = new Scene(loader.load());
                    
                    stage.setScene(scene);
                    stage.setTitle("SIP 通讯客户端 - 登录");
                    
                    statusLabel.setText("已注销");
                    
                } catch (IOException e) {
                    e.printStackTrace();
                    showAlert("错误", "注销失败: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 消息搜索
     */
    @FXML
    private void handleSearchMessage() {
        if (currentContact == null) {
            showInfoAlert("提示", "请先选择一个联系人");
            return;
        }
        
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("搜索消息");
        dialog.setHeaderText("在聊天记录中搜索");
        dialog.setContentText("请输入搜索关键词:");
        
        dialog.showAndWait().ifPresent(keyword -> {
            if (!keyword.trim().isEmpty()) {
                searchInMessages(keyword);
            }
        });
    }

    /**
     * 在消息中搜索
     */
    private void searchInMessages(String keyword) {
        chatBox.getChildren().clear();
        String lowerKeyword = keyword.toLowerCase();
        
        for (Message msg : currentContact.getMessages()) {
            if (msg.getContent().toLowerCase().contains(lowerKeyword)) {
                displayMessage(msg);
            }
        }
        
        if (chatBox.getChildren().isEmpty()) {
            Label noResultLabel = new Label("未找到包含 \"" + keyword + "\" 的消息");
            noResultLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 14px;");
            chatBox.getChildren().add(noResultLabel);
        }
    }

    /**
     * 显示表情符号选择器
     */
    @FXML
    private void handleShowEmoji() {
        String[] emojis = {"😀", "😊", "😂", "😍", "😭", "😎", "🤔", "👍", "👎", "❤️", "🎉", "🔥"};
        
        ChoiceDialog<String> dialog = new ChoiceDialog<>(emojis[0], emojis);
        dialog.setTitle("选择表情");
        dialog.setHeaderText("选择一个表情符号");
        dialog.setContentText("表情:");
        
        dialog.showAndWait().ifPresent(emoji -> {
            messageInput.appendText(emoji);
        });
    }

    /**
     * 附加文件（占位功能）
     */
    @FXML
    private void handleAttachFile() {
        showInfoAlert("功能开发中", "文件传输功能正在开发中，敬请期待！");
    }

    /**
     * 应用设置
     */
    private void applySettings() {
        statusLabel.setText("设置已更新");
    }

    private void showInfoAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * 从数据库加载联系人
     */
    private void loadContactsFromDatabase() {
        List<Contact> savedContacts = database.loadContacts();
        for (Contact contact : savedContacts) {
            allContacts.add(contact);
            // 加载消息历史
            if (SettingsController.isHistorySaveEnabled()) {
                List<Message> messages = database.loadMessages(contact.getUserId());
                contact.getMessages().addAll(messages);
            }
        }
    }

    /**
     * 设置联系人列表右键菜单
     */
    private void setupContextMenu() {
        contactListView.setCellFactory(lv -> {
            ContactCell cell = new ContactCell();
            ContextMenu contextMenu = new ContextMenu();
            
            MenuItem editItem = new MenuItem("编辑");
            editItem.setOnAction(e -> {
                Contact contact = cell.getItem();
                if (contact != null) {
                    handleEditContact(contact);
                }
            });
            
            MenuItem deleteItem = new MenuItem("删除");
            deleteItem.setOnAction(e -> {
                Contact contact = cell.getItem();
                if (contact != null) {
                    handleDeleteContact(contact);
                }
            });
            
            contextMenu.getItems().addAll(editItem, deleteItem);
            
            cell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
                if (isNowEmpty) {
                    cell.setContextMenu(null);
                } else {
                    cell.setContextMenu(contextMenu);
                }
            });
            
            return cell;
        });
    }

    /**
     * 编辑联系人
     */
    private void handleEditContact(Contact contact) {
        TextInputDialog dialog = new TextInputDialog(contact.getDisplayName());
        dialog.setTitle("编辑联系人");
        dialog.setHeaderText("编辑联系人昵称");
        dialog.setContentText("昵称:");
        
        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.trim().isEmpty()) {
                contact.setDisplayName(newName);
                database.saveContact(contact);
                contactListView.refresh();
                if (currentContact != null && currentContact.equals(contact)) {
                    chatTitleLabel.setText(newName);
                }
                showInfoAlert("成功", "联系人昵称已更新");
            }
        });
    }

    /**
     * 删除联系人
     */
    private void handleDeleteContact(Contact contact) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText("删除联系人");
        alert.setContentText("确定要删除联系人 \"" + contact.getDisplayName() + "\" 吗？\n这将删除所有聊天记录。");
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // 从数据库删除
                database.deleteContact(contact.getUserId());
                
                // 从列表删除
                allContacts.remove(contact);
                contacts.remove(contact);
                
                // 如果是当前联系人，清空聊天区域
                if (currentContact != null && currentContact.equals(contact)) {
                    currentContact = null;
                    chatBox.getChildren().clear();
                    chatTitleLabel.setText("选择联系人开始聊天");
                    messageInput.setDisable(true);
                    sendButton.setDisable(true);
                    callButton.setDisable(true);
                    videoCallButton.setDisable(true);
                }
                
                showInfoAlert("成功", "联系人已删除");
            }
        });
    }

    /**
     * 联系人列表单元格
     */
    private static class ContactCell extends ListCell<Contact> {
        @Override
        protected void updateItem(Contact contact, boolean empty) {
            super.updateItem(contact, empty);
            
            if (empty || contact == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox vbox = new VBox(5);
                
                HBox topLine = new HBox();
                Label nameLabel = new Label(contact.getDisplayName());
                nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
                HBox.setHgrow(nameLabel, Priority.ALWAYS);
                
                Label timeLabel = new Label(contact.getLastMessageTime() != null 
                    ? contact.getLastMessageTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                    : "");
                timeLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 11px;");
                
                topLine.getChildren().addAll(nameLabel, timeLabel);
                
                HBox bottomLine = new HBox();
                Label msgLabel = new Label(contact.getLastMessage() != null ? contact.getLastMessage() : "");
                msgLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");
                msgLabel.setMaxWidth(200);
                HBox.setHgrow(msgLabel, Priority.ALWAYS);
                
                if (contact.getUnreadCount() > 0) {
                    Label badge = new Label(String.valueOf(contact.getUnreadCount()));
                    badge.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white; " +
                                 "-fx-background-radius: 10; -fx-padding: 2 6 2 6; -fx-font-size: 11px;");
                    bottomLine.getChildren().addAll(msgLabel, badge);
                } else {
                    bottomLine.getChildren().add(msgLabel);
                }
                
                vbox.getChildren().addAll(topLine, bottomLine);
                setGraphic(vbox);
            }
        }
    }
}
