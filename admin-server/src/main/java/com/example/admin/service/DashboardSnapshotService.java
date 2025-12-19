package com.example.admin.service;

import com.example.admin.entity.CallRecord;
import com.example.admin.entity.DashboardSnapshot;
import com.example.admin.entity.Message;
import com.example.admin.entity.StatsSummary;
import com.example.admin.entity.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DashboardSnapshotService {

    private final StatsService statsService;
    private final UserService userService;
    private final CallRecordService callRecordService;
    private final MessageService messageService;

    public DashboardSnapshotService(
            StatsService statsService,
            UserService userService,
            CallRecordService callRecordService,
            MessageService messageService) {
        this.statsService = statsService;
        this.userService = userService;
        this.callRecordService = callRecordService;
        this.messageService = messageService;
    }

    public DashboardSnapshot capture() {
        StatsSummary stats = statsService.snapshot();
        List<User> users = userService.listUsers();
        List<CallRecord> calls = callRecordService.listCallRecords();
        List<Message> messages = messageService.listAll();

        return new DashboardSnapshot(stats, users, calls, messages, Instant.now());
    }
}