package com.ke.deepseektools.tools;

import com.ke.deepseektools.service.HumanNotificationService;
import com.ke.deepseektools.service.HumanNotificationService.NotificationRecord;
import com.ke.deepseektools.service.OrderLookupService;
import com.ke.deepseektools.service.OrderLookupService.OrderMatch;
import com.ke.deepseektools.service.WorkOrderService;
import com.ke.deepseektools.service.WorkOrderService.WorkOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class FlightChangeWorkflowTools {

    private static final Logger log = LoggerFactory.getLogger(FlightChangeWorkflowTools.class);

    private final OrderLookupService orderLookupService;
    private final WorkOrderService workOrderService;
    private final HumanNotificationService notificationService;

    public FlightChangeWorkflowTools(OrderLookupService orderLookupService, WorkOrderService workOrderService,
            HumanNotificationService notificationService) {
        this.orderLookupService = orderLookupService;
        this.workOrderService = workOrderService;
        this.notificationService = notificationService;
    }

    @Tool(description = "根据航变邮件中提取的乘机人、票号、PNR、原航班、航线、出发日期关联本地订单。航变邮件处理时必须先调用。")
    public OrderMatch findRelatedOrder(
            @ToolParam(description = "乘机人姓名，未识别则传未识别") String passengerName,
            @ToolParam(description = "票号，例如 781-1234567890，未识别则传未识别") String ticketNo,
            @ToolParam(description = "PNR 或预订编码，例如 H8K2Q9，未识别则传未识别") String bookingCode,
            @ToolParam(description = "原航班号，例如 MU5101，未识别则传未识别") String originalFlightNo,
            @ToolParam(description = "航线，例如 北京首都T2-上海虹桥T2，未识别则传未识别") String route,
            @ToolParam(description = "出发日期，格式 yyyy-MM-dd，未识别则传未识别") String departureDate) {

        log.info("Tool called: findRelatedOrder(passengerName={}, ticketNo={}, bookingCode={}, originalFlightNo={})",
                passengerName, ticketNo, bookingCode, originalFlightNo);
        return orderLookupService.findRelatedOrder(passengerName, ticketNo, bookingCode, originalFlightNo, route,
                departureDate);
    }

    @Tool(description = "为已识别的航变邮件生成工单。关联订单后必须调用；如果未匹配订单，也要创建待人工核对工单。")
    public WorkOrder createFlightChangeWorkOrder(
            @ToolParam(description = "订单号；未匹配订单时传未匹配") String orderNo,
            @ToolParam(description = "乘机人姓名") String passengerName,
            @ToolParam(description = "原航班号") String originalFlightNo,
            @ToolParam(description = "新航班号") String newFlightNo,
            @ToolParam(description = "出发日期，格式 yyyy-MM-dd") String departureDate,
            @ToolParam(description = "航线") String route,
            @ToolParam(description = "变更原因") String changeReason,
            @ToolParam(description = "影响说明，例如起飞时间延后 2 小时 30 分钟") String impact,
            @ToolParam(description = "优先级，P1/P2/P3。临近起飞、未匹配订单或信息缺失时用 P1 或 P2") String priority) {

        log.info("Tool called: createFlightChangeWorkOrder(orderNo={}, passengerName={}, priority={})",
                orderNo, passengerName, priority);
        return workOrderService.createFlightChangeWorkOrder(orderNo, passengerName, originalFlightNo, newFlightNo,
                departureDate, route, changeReason, impact, priority);
    }

    @Tool(description = "通知人工坐席处理航变工单。工单创建后必须调用。")
    public NotificationRecord notifyHumanAgent(
            @ToolParam(description = "工单号，例如 FC-20260530-1001") String workOrderId,
            @ToolParam(description = "优先级，P1/P2/P3") String priority,
            @ToolParam(description = "发给人工的处理说明，包含旅客、订单、航变内容和需要确认的问题") String message) {

        log.info("Tool called: notifyHumanAgent(workOrderId={}, priority={})", workOrderId, priority);
        return notificationService.notifyHumanAgent(workOrderId, priority, message);
    }
}
