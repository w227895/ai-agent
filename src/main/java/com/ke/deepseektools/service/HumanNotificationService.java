package com.ke.deepseektools.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class HumanNotificationService {

    private final List<NotificationRecord> notifications = Collections.synchronizedList(new ArrayList<>());

    public NotificationRecord notifyHumanAgent(String workOrderId, String priority, String message) {
        NotificationRecord record = new NotificationRecord(
                "NT-" + OffsetDateTime.now().toLocalTime().toSecondOfDay(),
                workOrderId,
                priority == null || priority.isBlank() ? "P2" : priority.trim().toUpperCase(),
                message,
                "人工坐席队列",
                OffsetDateTime.now(),
                "已发送");
        notifications.add(record);
        return record;
    }

    public record NotificationRecord(
            String notificationId,
            String workOrderId,
            String priority,
            String message,
            String channel,
            OffsetDateTime sentAt,
            String status) {
    }
}
