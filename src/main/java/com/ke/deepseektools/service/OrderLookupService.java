package com.ke.deepseektools.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

@Service
public class OrderLookupService {

    private final List<OrderRecord> orders = List.of(
            new OrderRecord("ORD-20260530-1001", "张三", "781-1234567890", "H8K2Q9",
                    "MU5101", "北京首都T2-上海虹桥T2", LocalDate.of(2026, 6, 2), "13800000001"),
            new OrderRecord("ORD-20260530-1002", "李四", "784-9876543210", "K7P9M2",
                    "CZ3151", "广州白云T2-北京大兴", LocalDate.of(2026, 6, 3), "13800000002"),
            new OrderRecord("ORD-20260530-1003", "王五", "999-2468135790", "N3X6T8",
                    "CA1831", "北京首都T3-深圳宝安", LocalDate.of(2026, 6, 5), "13800000003"));

    public OrderMatch findRelatedOrder(String passengerName, String ticketNo, String bookingCode,
            String originalFlightNo, String route, String departureDate) {

        return orders.stream()
                .map(order -> score(order, passengerName, ticketNo, bookingCode, originalFlightNo, route, departureDate))
                .filter(match -> match.score() > 0)
                .max((left, right) -> Integer.compare(left.score(), right.score()))
                .orElse(new OrderMatch(false, null, 0, "未匹配到订单，请人工核对票号、PNR 或乘机人信息。"));
    }

    private OrderMatch score(OrderRecord order, String passengerName, String ticketNo, String bookingCode,
            String originalFlightNo, String route, String departureDate) {

        int score = 0;
        StringBuilder reason = new StringBuilder();

        if (same(order.ticketNo(), ticketNo)) {
            score += 50;
            reason.append("票号匹配；");
        }
        if (same(order.bookingCode(), bookingCode)) {
            score += 30;
            reason.append("PNR匹配；");
        }
        if (same(order.passengerName(), passengerName)) {
            score += 10;
            reason.append("乘机人匹配；");
        }
        if (same(order.originalFlightNo(), originalFlightNo)) {
            score += 8;
            reason.append("原航班匹配；");
        }
        if (containsSameRoute(order.route(), route)) {
            score += 5;
            reason.append("航线匹配；");
        }
        if (same(order.departureDate().toString(), departureDate)) {
            score += 5;
            reason.append("出发日期匹配；");
        }

        boolean matched = score >= 50;
        return new OrderMatch(matched, matched ? order : null, score,
                reason.isEmpty() ? "候选订单匹配度不足。" : reason.toString());
    }

    private boolean same(String left, String right) {
        return left != null && right != null && Objects.equals(clean(left), clean(right));
    }

    private boolean containsSameRoute(String orderRoute, String extractedRoute) {
        if (orderRoute == null || extractedRoute == null) {
            return false;
        }
        String cleanedOrderRoute = clean(orderRoute);
        String cleanedExtractedRoute = clean(extractedRoute);
        return cleanedOrderRoute.contains(cleanedExtractedRoute) || cleanedExtractedRoute.contains(cleanedOrderRoute);
    }

    private String clean(String value) {
        return value.replace(" ", "")
                .replace("-", "")
                .replace("：", ":")
                .trim()
                .toUpperCase();
    }

    public record OrderRecord(
            String orderNo,
            String passengerName,
            String ticketNo,
            String bookingCode,
            String originalFlightNo,
            String route,
            LocalDate departureDate,
            String contactMobile) {
    }

    public record OrderMatch(boolean matched, OrderRecord order, int score, String reason) {
    }
}
