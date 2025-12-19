package com.example.admin.service;

import com.example.admin.entity.Message;
import com.example.admin.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    // 发送消息
    public Message sendMessage(String sender, String receiver, String content) {
        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());
        message.setRead(false); // 刚发出去，默认未读

        return messageRepository.save(message);
    }

    // 获取两个人的聊天历史
    public List<Message> getChatHistory(String user1, String user2) {
        return messageRepository.findChatHistory(user1, user2);
    }

    // 获取所有消息（Dashboard 用）
    public List<Message> listAll() {
        return messageRepository.findAll();
    }
}
