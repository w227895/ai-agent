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

    private final ChatClient chatClient;

    public AiController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/function-call")
    public AiResponse functionCall(
            @RequestParam(defaultValue = "我明天去北京出差，天气怎么样？顺便帮我算一下 3 件 199 元商品打 8 折后一共多少钱。")
            String message) {

        String answer = chatClient.prompt()
                .user(message)
                .call()
                .content();

        return new AiResponse(message, answer);
    }

    @PostMapping("/function-call")
    public AiResponse functionCall(@RequestBody AiRequest request) {
        String message = request.message();
        if (message == null || message.isBlank()) {
            message = "我明天去北京出差，天气怎么样？顺便帮我算一下 3 件 199 元商品打 8 折后一共多少钱。";
        }

        String answer = chatClient.prompt()
                .user(message)
                .call()
                .content();

        return new AiResponse(message, answer);
    }

    public record AiRequest(String message) {
    }

    public record AiResponse(String message, String answer) {
    }
}
