package com.example.admin.entity;

import java.time.Instant;
import java.util.List;

public class DashboardSnapshot {
    private StatsSummary stats;
    private List<User> users;
    private List<CallRecord> calls;
    private List<Message> messages; // 新增：消息记录
    private Instant timestamp;

    // 构造方法 (兼容旧版)
    public DashboardSnapshot(StatsSummary stats, List<User> users, List<CallRecord> calls, Instant timestamp) {
        this.stats = stats;
        this.users = users;
        this.calls = calls;
        this.messages = null;
        this.timestamp = timestamp;
    }

    // 新构造方法 (包含消息)
    public DashboardSnapshot(StatsSummary stats, List<User> users, List<CallRecord> calls, List<Message> messages,
            Instant timestamp) {
        this.stats = stats;
        this.users = users;
        this.calls = calls;
        this.messages = messages;
        this.timestamp = timestamp;
    }

    // Getter 和 Setter
    public StatsSummary getStats() {
        return stats;
    }

    public void setStats(StatsSummary stats) {
        this.stats = stats;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public List<CallRecord> getCalls() {
        return calls;
    }

    public void setCalls(List<CallRecord> calls) {
        this.calls = calls;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}