package com.ke.deepseektools.prompt;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LlmPromptService {

    private static final DateTimeFormatter PROMPT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatClient chatClient;
    private final LlmPromptRepository repository;

    public LlmPromptService(ChatClient chatClient, LlmPromptRepository repository) {
        this.chatClient = chatClient;
        this.repository = repository;
    }

    public PageResult<LlmPrompt> list(int page, int size, String keyword) {
        return repository.findPage(page, size, keyword);
    }

    public LlmPrompt get(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("提示词不存在: " + id));
    }

    public LlmPrompt save(LlmPrompt prompt) {
        return repository.save(prompt);
    }

    public void delete(long id) {
        repository.delete(id);
    }

    public void setActive(long id, boolean active) {
        repository.setActive(id, active);
    }

    public TestResult test(TestRequest request) {
        if (request == null || isBlank(request.emailContent())) {
            throw new IllegalArgumentException("邮件内容不能为空");
        }
        LlmPrompt prompt = resolvePrompt(request);
        String emailContent = request.emailContent().trim();
        String systemPrompt = blankToEmpty(prompt.systemPrompt()).trim();
        String userPrompt = renderUserPrompt(prompt.userPrompt(), emailContent);
        String finalPrompt = renderFinalPrompt(systemPrompt, userPrompt);
        long startedAt = System.currentTimeMillis();
        String result = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        long elapsedMs = System.currentTimeMillis() - startedAt;
        return new TestResult(prompt, emailContent, finalPrompt, result == null ? "" : result, elapsedMs, "SUCCESS");
    }

    private LlmPrompt resolvePrompt(TestRequest request) {
        if (request.promptId() != null) {
            return get(request.promptId());
        }
        if (!isBlank(request.promptCode())) {
            return repository.findActiveByCode(request.promptCode())
                    .orElseThrow(() -> new IllegalArgumentException("未找到启用的提示词代码: " + request.promptCode()));
        }
        if (!isBlank(request.sender())) {
            return matchBySender(request.sender())
                    .orElseThrow(() -> new IllegalArgumentException("未匹配到发件人对应的启用提示词"));
        }
        return repository.findActivePrompts().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("没有可用的启用提示词"));
    }

    private java.util.Optional<LlmPrompt> matchBySender(String sender) {
        String normalizedSender = sender.trim().toLowerCase(Locale.ROOT);
        return repository.findActivePrompts().stream()
                .filter(prompt -> "2".equals(prompt.codeType()))
                .filter(prompt -> promptCodeMatches(prompt.promptCode(), normalizedSender))
                .min(Comparator.comparingInt(LlmPrompt::priority).thenComparing(
                        prompt -> prompt.id() == null ? Long.MAX_VALUE : prompt.id()));
    }

    private boolean promptCodeMatches(String promptCode, String sender) {
        if (promptCode == null || promptCode.isBlank()) {
            return false;
        }
        for (String item : promptCode.split(",")) {
            String token = item.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }
            if (sender.equals(token) || sender.contains(token)) {
                return true;
            }
            if (token.startsWith("@") && sender.endsWith(token)) {
                return true;
            }
        }
        return false;
    }

    private String renderUserPrompt(String template, String emailContent) {
        String effectiveTemplate = isBlank(template)
                ? "请处理下面的邮件内容，只返回结果：\n\n{email_content}"
                : template;
        String currentTime = LocalDateTime.now().format(PROMPT_TIME_FORMATTER);
        return effectiveTemplate
                .replace("{current_time}", currentTime)
                .replace("{email_content}", emailContent)
                .replace("{mail_content}", emailContent)
                .replace("{sms_content}", emailContent)
                .trim();
    }

    private String renderFinalPrompt(String systemPrompt, String userPrompt) {
        return """
                System Prompt

                %s

                User Prompt

                %s
                """.formatted(systemPrompt, userPrompt).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record TestRequest(Long promptId, String promptCode, String sender, String emailContent) {
    }

    public record TestResult(
            LlmPrompt prompt,
            String emailContent,
            String finalPrompt,
            String result,
            long elapsedMs,
            String status) {
    }
}
