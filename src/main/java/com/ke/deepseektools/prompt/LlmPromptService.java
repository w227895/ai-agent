package com.ke.deepseektools.prompt;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LlmPromptService {

    private static final DateTimeFormatter PROMPT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatClient chatClient;
    private final LlmPromptRepository repository;
    private final LlmPromptScenarioRepository scenarioRepository;
    private final LlmPromptFewShotRepository fewShotRepository;
    private final LlmOutputSchemaRepository outputSchemaRepository;

    public LlmPromptService(ChatClient chatClient, LlmPromptRepository repository,
            LlmPromptScenarioRepository scenarioRepository, LlmPromptFewShotRepository fewShotRepository,
            LlmOutputSchemaRepository outputSchemaRepository) {
        this.chatClient = chatClient;
        this.repository = repository;
        this.scenarioRepository = scenarioRepository;
        this.fewShotRepository = fewShotRepository;
        this.outputSchemaRepository = outputSchemaRepository;
    }

    public PageResult<LlmPrompt> list(int page, int size, String keyword) {
        return list(page, size, keyword, null, null);
    }

    public PageResult<LlmPrompt> list(int page, int size, String keyword, Long sceneId, Boolean active) {
        return repository.findPage(page, size, keyword, sceneId, active);
    }

    public PageResult<LlmPromptScenario> listScenes(int page, int size, String keyword) {
        return scenarioRepository.findPage(page, size, keyword);
    }

    public java.util.List<LlmPromptScenario> listActiveScenes() {
        return scenarioRepository.findActiveScenarios();
    }

    public LlmPromptScenario getScene(long id) {
        return scenarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("场景不存在: " + id));
    }

    public LlmPromptScenario saveScene(LlmPromptScenario scenario) {
        return scenarioRepository.save(scenario);
    }

    public void setSceneActive(long id, boolean active) {
        scenarioRepository.setActive(id, active);
    }

    public void deleteScene(long id) {
        long promptCount = repository.countBySceneId(id);
        if (promptCount > 0) {
            throw new IllegalArgumentException("该场景已关联 " + promptCount + " 条提示词，请先改为禁用或调整提示词场景");
        }
        scenarioRepository.delete(id);
    }

    public LlmPrompt get(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("提示词不存在: " + id));
    }

    public PromptDictionaries.DictionaryResult dictionaries() {
        PromptDictionaries.DictionaryResult result = scenarioRepository.dictionaries();
        if (result.scenarios().isEmpty()) {
            return PromptDictionaries.all();
        }
        return result;
    }

    public LlmPrompt save(LlmPrompt prompt) {
        if (prompt == null || prompt.sceneId() == null) {
            throw new IllegalArgumentException("请选择场景");
        }
        getScene(prompt.sceneId());
        if (prompt.outputSchemaId() != null) {
            getOutputSchema(prompt.outputSchemaId());
        }
        return repository.save(prompt);
    }

    public void delete(long id) {
        fewShotRepository.deleteByPromptId(id);
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
        LlmOutputSchema outputSchema = resolveOutputSchema(prompt);
        String systemPrompt = appendOutputSchema(
                blankToEmpty(prompt.systemPrompt()).trim(),
                renderOutputSchema(outputSchema));
        String fewShot = renderFewShot(prompt.id(), emailContent);
        String userPrompt = renderUserPrompt(prompt.userPrompt(), fewShot, emailContent);
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

    public PageResult<LlmPromptFewShot> listFewShots(int page, int size, String keyword, Long promptId, Boolean active) {
        return fewShotRepository.findPage(page, size, keyword, promptId, active);
    }

    public PageResult<LlmOutputSchema> listOutputSchemas(
            int page, int size, String keyword, Long sceneId, Boolean active) {
        return outputSchemaRepository.findPage(page, size, keyword, sceneId, active);
    }

    public java.util.List<LlmOutputSchema> listActiveOutputSchemas() {
        return outputSchemaRepository.findActiveSchemas();
    }

    public LlmOutputSchema getOutputSchema(long id) {
        return outputSchemaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("输出结构不存在: " + id));
    }

    public LlmOutputSchema saveOutputSchema(LlmOutputSchema schema) {
        if (schema != null && schema.sceneId() != null) {
            getScene(schema.sceneId());
        }
        return outputSchemaRepository.save(schema);
    }

    public void setOutputSchemaActive(long id, boolean active) {
        outputSchemaRepository.setActive(id, active);
    }

    public void deleteOutputSchema(long id) {
        long promptCount = outputSchemaRepository.countPromptReferences(id);
        if (promptCount > 0) {
            throw new IllegalArgumentException("该输出结构已关联 " + promptCount + " 条提示词，请先调整提示词关联");
        }
        outputSchemaRepository.delete(id);
    }

    public LlmPromptFewShot getFewShot(long id) {
        return fewShotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Few-shot 不存在: " + id));
    }

    public LlmPromptFewShot saveFewShot(LlmPromptFewShot fewShot) {
        if (fewShot == null || fewShot.promptId() == null) {
            throw new IllegalArgumentException("请选择提示词");
        }
        get(fewShot.promptId());
        return fewShotRepository.save(fewShot);
    }

    public void setFewShotActive(long id, boolean active) {
        fewShotRepository.setActive(id, active);
    }

    public void deleteFewShot(long id) {
        fewShotRepository.delete(id);
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

    private Optional<LlmPrompt> matchBySender(String sender) {
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

    private String renderUserPrompt(String template, String fewShot, String emailContent) {
        String effectiveTemplate = isBlank(template)
                ? "请处理下面的邮件内容，只返回结果：\n\n{email_content}"
                : template;
        String mainPrompt = renderTemplate(effectiveTemplate, emailContent);
        String renderedFewShot = renderTemplate(fewShot, emailContent);
        if (isBlank(renderedFewShot)) {
            return mainPrompt;
        }
        return """
                %s

                Few-shot Examples

                %s
                """.formatted(mainPrompt, renderedFewShot).trim();
    }

    private String renderFewShot(Long promptId, String emailContent) {
        if (promptId == null) {
            return "";
        }
        return fewShotRepository.findActiveByPromptId(promptId).stream()
                .map(item -> renderTemplate(item.content(), emailContent))
                .filter(value -> !isBlank(value))
                .reduce((left, right) -> left + "\n\n---\n\n" + right)
                .orElse("");
    }

    private LlmOutputSchema resolveOutputSchema(LlmPrompt prompt) {
        if (prompt.outputSchemaId() == null) {
            return null;
        }
        return outputSchemaRepository.findById(prompt.outputSchemaId())
                .filter(LlmOutputSchema::active)
                .orElse(null);
    }

    private String renderOutputSchema(LlmOutputSchema outputSchema) {
        if (outputSchema == null) {
            return "";
        }
        String promptFragment = blankToEmpty(outputSchema.promptFragment()).trim();
        String schemaContent = blankToEmpty(outputSchema.schemaContent()).trim();
        String sampleOutput = blankToEmpty(outputSchema.sampleOutput()).trim();
        StringBuilder builder = new StringBuilder();
        if (!promptFragment.isBlank()) {
            builder.append(promptFragment);
        }
        appendSection(builder, "Schema Definition", schemaContent);
        appendSection(builder, "Sample Output", sampleOutput);
        return builder.toString().trim();
    }

    private String appendOutputSchema(String systemPrompt, String outputSchemaPrompt) {
        if (isBlank(outputSchemaPrompt)) {
            return systemPrompt;
        }
        if (isBlank(systemPrompt)) {
            return "Output Schema\n\n" + outputSchemaPrompt;
        }
        return """
                %s

                Output Schema

                %s
                """.formatted(systemPrompt, outputSchemaPrompt).trim();
    }

    private void appendSection(StringBuilder builder, String title, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(title).append("\n").append(content);
    }

    private String renderTemplate(String template, String emailContent) {
        if (template == null) {
            return "";
        }
        String currentTime = LocalDateTime.now().format(PROMPT_TIME_FORMATTER);
        return template
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
