package com.ke.deepseektools.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class AiController {

    private static final String DEFAULT_MAIL = """
            Subject: 航班变更通知 MU5101

            旅客张三，票号 781-1234567890，PNR H8K2Q9。
            原航班 MU5101 因计划调整变更为 MU5115，出发日期 2026-06-02，航线北京首都T2至上海虹桥T2。
            请联系旅客确认是否接受变更。
            """;

    private final ChatClient chatClient;

    public AiController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/function-call")
    public AiResponse functionCall(@RequestParam(defaultValue = DEFAULT_MAIL) String message) {
        return processMailLikeText(message);
    }

    @PostMapping("/function-call")
    public AiResponse functionCall(@RequestBody AiRequest request) {
        String message = request.message();
        if (message == null || message.isBlank()) {
            message = DEFAULT_MAIL;
        }
        return processMailLikeText(message);
    }

    private AiResponse processMailLikeText(String message) {
        String answer = chatClient.prompt()
                .user("""
                        请按航变邮件处理流程处理下面内容：

                        %s
                        """.formatted(message))
                .call()
                .content();
        return new AiResponse(message, answer);
    }

    public record AiRequest(String message) {
    }

    public record AiResponse(String message, String answer) {
    }
}
