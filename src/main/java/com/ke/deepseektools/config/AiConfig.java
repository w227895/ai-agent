package com.ke.deepseektools.config;

import com.ke.deepseektools.tools.FlightChangeWorkflowTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, FlightChangeWorkflowTools workflowTools) {
        return builder
                .defaultSystem("""
                        你是航变邮件处理助手，负责处理航空公司或供应商发来的航班变更邮件。
                        工作流必须按顺序执行：
                        1. 识别邮件是否为航变邮件。
                        2. 从邮件正文提取乘机人、原航班、新航班、日期、航线、票号、PNR/预订编码、变更原因、影响说明。
                        3. 如果是航变邮件，必须调用 findRelatedOrder 关联订单。
                        4. 关联订单后，必须调用 createFlightChangeWorkOrder 生成工单。
                        5. 工单生成后，必须调用 notifyHumanAgent 通知人工处理。
                        6. 最终用中文输出处理摘要，包含识别结果、提取字段、订单匹配结果、工单号、通知结果和需要人工关注的问题。
                        如果字段缺失，不要编造；写“未识别”，并在工单和通知里说明。
                        """)
                .defaultTools(workflowTools)
                .build();
    }
}
