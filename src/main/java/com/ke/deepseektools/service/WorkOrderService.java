package com.ke.deepseektools.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

@Service
public class WorkOrderService {

    private final AtomicInteger sequence = new AtomicInteger(1000);
    private final Map<String, WorkOrder> workOrders = new ConcurrentHashMap<>();

    public WorkOrder createFlightChangeWorkOrder(String orderNo, String passengerName, String originalFlightNo,
            String newFlightNo, String departureDate, String route, String changeReason, String impact,
            String priority) {

        String workOrderId = "FC-" + OffsetDateTime.now().toLocalDate().toString().replace("-", "")
                + "-" + sequence.incrementAndGet();
        WorkOrder workOrder = new WorkOrder(workOrderId, orderNo, passengerName, originalFlightNo, newFlightNo,
                departureDate, route, changeReason, impact, normalizePriority(priority), "待人工处理",
                OffsetDateTime.now());
        workOrders.put(workOrderId, workOrder);
        return workOrder;
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "P2";
        }
        return priority.trim().toUpperCase();
    }

    public record WorkOrder(
            String workOrderId,
            String orderNo,
            String passengerName,
            String originalFlightNo,
            String newFlightNo,
            String departureDate,
            String route,
            String changeReason,
            String impact,
            String priority,
            String status,
            OffsetDateTime createdAt) {
    }
}
