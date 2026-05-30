package com.ke.deepseektools.config;

import com.ke.deepseektools.tools.LocalBusinessTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, LocalBusinessTools localBusinessTools) {
        return builder
                .defaultSystem("""
                        你是一个 Spring AI function calling 演示助手。
                        当用户询问天气、城市出行建议、订单金额或折扣计算时，优先调用可用工具获取结果。
                        回答要简洁，并说明你使用了哪个本地工具。
                        """)
                .defaultTools(localBusinessTools)
                .build();
    }
}
