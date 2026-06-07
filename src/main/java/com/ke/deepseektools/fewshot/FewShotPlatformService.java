package com.ke.deepseektools.fewshot;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class FewShotPlatformService {

    private static final DateTimeFormatter PROMPT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatClient chatClient;
    private final FewShotScenarioRepository scenarioRepository;
    private final ObjectMapper objectMapper;

    public FewShotPlatformService(ChatClient chatClient, FewShotScenarioRepository scenarioRepository,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.scenarioRepository = scenarioRepository;
        this.objectMapper = objectMapper;
    }

    public List<FewShotScenario> listScenarios() {
        return scenarioRepository.findAll();
    }

    public PageResult<FewShotScenario> listScenariosPage(int page, int size, String keyword) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        long total = scenarioRepository.count(keyword);
        List<FewShotScenario> items = scenarioRepository.findPage(keyword, normalizedPage, normalizedSize);
        return new PageResult<>(items, total, normalizedPage, normalizedSize);
    }

    public FewShotScenario getScenario(String code) {
        return scenarioRepository.findByCode(code)
                .orElseThrow(() -> new FewShotScenarioRepository.FewShotScenarioNotFoundException(code));
    }

    public FewShotScenario saveScenario(FewShotScenario scenario) {
        return scenarioRepository.save(scenario);
    }

    public void deleteScenario(String code) {
        scenarioRepository.deleteByCode(code);
    }

    public List<LlmPromptTemplate> listPromptTemplates() {
        return scenarioRepository.findAllPromptTemplates();
    }

    public LlmPromptTemplate savePromptTemplate(LlmPromptTemplate promptTemplate) {
        return scenarioRepository.savePromptTemplate(promptTemplate);
    }

    public List<LlmOutputSchema> listOutputSchemas() {
        return scenarioRepository.findAllOutputSchemas();
    }

    public LlmOutputSchema saveOutputSchema(LlmOutputSchema outputSchema) {
        return scenarioRepository.saveOutputSchema(outputSchema);
    }

    public FewShotScenario addExample(String scenarioCode, FewShotExample example) {
        return scenarioRepository.addExample(scenarioCode, example);
    }

    public List<FewShotFailureCase> listFailureCases(String scenarioCode) {
        getScenario(scenarioCode);
        return scenarioRepository.findFailureCases(scenarioCode);
    }

    public List<FewShotFailureCase> importFailureCases(String scenarioCode, List<FewShotFailureCase> failureCases) {
        List<FewShotFailureCase> normalized = failureCases == null ? List.of()
                : failureCases.stream()
                        .filter(failureCase -> failureCase != null
                                && !isBlank(failureCase.input())
                                && !isBlank(failureCase.expectedOutput()))
                        .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("failure cases cannot be empty");
        }
        return scenarioRepository.saveFailureCases(scenarioCode, normalized);
    }

    public List<FewShotFailureCase> importFailureCasesFromText(String scenarioCode, String rawText) {
        FewShotScenario scenario = getScenario(scenarioCode);
        if (isBlank(rawText)) {
            throw new IllegalArgumentException("failure case text cannot be blank");
        }
        String output = chatClient.prompt()
                .system("""
                        你是一个失败案例整理助手。
                        用户会粘贴一段自然语言描述，里面可能包含原始输入、模型错误输出、人工期望输出和问题说明。
                        请把它整理成失败案例 JSON 数组。
                        只返回 JSON 数组，不要 Markdown，不要解释。
                        数组元素字段必须是：
                        {
                          "input": "原始待解析文本；必须保留完整业务输入",
                          "actualOutput": "当前模型的错误输出；如果没有提供则返回空字符串",
                          "expectedOutput": "人工修正后的正确输出；如果用户只描述了正确规则，请尽量整理成明确输出或规则文本",
                          "problemNote": "失败原因或用户备注"
                        }
                        如果无法找到原始输入或期望输出，返回空数组。
                        """)
                .user("""
                        当前场景：
                        code: %s
                        name: %s
                        inputLabel: %s

                        用户粘贴的失败案例内容：
                        %s
                        """.formatted(
                        scenario.code(),
                        scenario.name(),
                        scenario.inputLabel(),
                        rawText.trim()))
                .call()
                .content();
        return importFailureCases(scenario.code(), parseFailureCaseArray(output));
    }

    public PromptOptimizationResult optimizePrompt(String scenarioCode) {
        FewShotScenario scenario = getScenario(scenarioCode);
        List<FewShotFailureCase> failureCases = scenarioRepository.findFailureCases(scenarioCode).stream()
                .limit(20)
                .toList();
        if (failureCases.isEmpty()) {
            throw new IllegalArgumentException("no failure cases imported for scenario: " + scenarioCode);
        }

        String output = chatClient.prompt()
                .system("""
                        你是一个严谨的 few-shot 提示词优化助手。
                        你的任务是基于当前场景配置和解析失败案例，给出低风险优化建议。
                        优先级：先建议新增 few-shot 示例，其次补充字段说明，再建议微调 system_prompt，最后才调整 user_prompt。
                        不要直接假设线上配置会被覆盖。
                        只返回一个 JSON 对象，不要 Markdown，不要解释性前后缀。
                        JSON 字段必须是：
                        {
                          "analysis": "失败原因与修改理由",
                          "suggestedSystemPrompt": "建议的完整 system_prompt；如果不建议修改则返回空字符串",
                          "suggestedUserPrompt": "建议的完整 user_prompt；如果不建议修改则返回空字符串",
                          "suggestedFieldDescriptionJson": "建议的字段说明 JSON；如果不建议修改则返回空字符串",
                          "suggestedExamples": [
                            {
                              "id": "failure-case-example-001",
                              "title": "示例标题",
                              "input": "失败案例输入",
                              "expectedOutput": "人工修正后的正确输出",
                              "tags": ["失败优化"]
                            }
                          ]
                        }
                        """)
                .user(buildOptimizationUserPrompt(scenario, failureCases))
                .call()
                .content();
        return parseOptimizationResult(scenario.code(), output);
    }

    public FewShotRunResult run(String scenarioCode, String input) {
        FewShotScenario scenario = getScenario(scenarioCode);
        String effectiveInput = input == null || input.isBlank()
                ? scenario.examples().stream().findFirst().map(FewShotExample::input).orElse("")
                : input.trim();
        long startedAt = System.currentTimeMillis();

        ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                .system(buildSystemPrompt(scenario))
                .user(buildUserPrompt(scenario, effectiveInput));

        try {
            String output = request.call().content();
            scenarioRepository.recordRun(scenario, effectiveInput, output, "SUCCESS", null,
                    System.currentTimeMillis() - startedAt);
            return new FewShotRunResult(scenario.code(), scenario.name(), scenario.toolProfile(), effectiveInput, output);
        } catch (RuntimeException exception) {
            scenarioRepository.recordRun(scenario, effectiveInput, null, "FAILED", exception.getMessage(),
                    System.currentTimeMillis() - startedAt);
            throw exception;
        }
    }

    public PromptPreview previewPrompt(String scenarioCode, String input) {
        FewShotScenario scenario = getScenario(scenarioCode);
        String effectiveInput = input == null ? "" : input.trim();
        return new PromptPreview(
                scenario.code(),
                scenario.name(),
                buildSystemPrompt(scenario),
                buildUserPrompt(scenario, effectiveInput));
    }

    private String buildOptimizationUserPrompt(FewShotScenario scenario, List<FewShotFailureCase> failureCases) {
        String currentUserPrompt = scenario.mainPrompt() == null ? "" : scenario.mainPrompt().userPrompt();
        String currentFieldDescription = scenario.outputSchema() == null ? "" : scenario.outputSchema().fieldDescriptionJson();
        return """
                当前场景：
                code: %s
                name: %s
                description: %s

                当前最终 System Prompt：
                %s

                当前 user_prompt 模板：
                %s

                当前输出结构字段说明：
                %s

                解析失败案例：
                %s

                请找出这些失败案例暴露的共性问题，并生成一个低风险优化建议。
                如果只需要新增 few-shot 示例，就让 suggestedSystemPrompt、suggestedUserPrompt、suggestedFieldDescriptionJson 返回空字符串。
                """.formatted(
                scenario.code(),
                scenario.name(),
                scenario.description(),
                buildSystemPrompt(scenario),
                currentUserPrompt,
                currentFieldDescription,
                renderFailureCases(failureCases));
    }

    private String renderFailureCases(List<FewShotFailureCase> failureCases) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < failureCases.size(); i++) {
            FewShotFailureCase failureCase = failureCases.get(i);
            builder.append("失败案例 ").append(i + 1).append('\n')
                    .append("问题备注：").append(blankToEmpty(failureCase.problemNote())).append('\n')
                    .append("输入：\n").append(blankToEmpty(failureCase.input())).append('\n')
                    .append("当前错误输出：\n").append(blankToEmpty(failureCase.actualOutput())).append('\n')
                    .append("人工期望输出：\n").append(blankToEmpty(failureCase.expectedOutput())).append("\n\n");
        }
        return builder.toString().trim();
    }

    private PromptOptimizationResult parseOptimizationResult(String scenarioCode, String output) {
        String rawOutput = output == null ? "" : output.trim();
        try {
            PromptOptimizationResult parsed = objectMapper.readValue(extractJsonObject(rawOutput),
                    PromptOptimizationResult.class);
            return new PromptOptimizationResult(
                    scenarioCode,
                    blankToEmpty(parsed.analysis()),
                    blankToEmpty(parsed.suggestedSystemPrompt()),
                    blankToEmpty(parsed.suggestedUserPrompt()),
                    blankToEmpty(parsed.suggestedFieldDescriptionJson()),
                    parsed.suggestedExamples() == null ? List.of() : parsed.suggestedExamples(),
                    rawOutput);
        } catch (JsonProcessingException exception) {
            return new PromptOptimizationResult(
                    scenarioCode,
                    "模型返回结果不是可解析 JSON，请查看 rawResult 后手动处理。",
                    "",
                    "",
                    "",
                    List.of(),
                    rawOutput);
        }
    }

    private List<FewShotFailureCase> parseFailureCaseArray(String output) {
        String rawOutput = output == null ? "" : output.trim();
        try {
            FewShotFailureCase[] parsed = objectMapper.readValue(extractJsonArray(rawOutput),
                    FewShotFailureCase[].class);
            return List.of(parsed);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("AI did not return valid failure case JSON");
        }
    }

    private String extractJsonObject(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                trimmed = trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String extractJsonArray(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                trimmed = trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String buildSystemPrompt(FewShotScenario scenario) {
        String baseSystemPrompt = scenario.mainPrompt() == null || isBlank(scenario.mainPrompt().systemPrompt())
                ? buildLegacySystemPrompt(scenario)
                : scenario.mainPrompt().systemPrompt().trim();
        return joinPromptSections(
                baseSystemPrompt,
                renderOutputSchema(scenario.outputSchema(), scenario.outputContract()),
                renderExamples(scenario.examples()));
    }

    private String buildUserPrompt(FewShotScenario scenario, String input) {
        String effectiveInput = input == null ? "" : input;
        LlmPromptTemplate mainPrompt = scenario.mainPrompt();
        if (mainPrompt == null || isBlank(mainPrompt.userPrompt())) {
            return """
                    请按当前场景配置处理下面输入。
                    %s：
                    %s
                    """.formatted(scenario.inputLabel(), effectiveInput).trim();
        }
        return mainPrompt.userPrompt()
                .replace("{current_time}", LocalDateTime.now().format(PROMPT_TIME_FORMATTER))
                .replace("{email_content}", effectiveInput)
                .trim();
    }

    private String buildLegacySystemPrompt(FewShotScenario scenario) {
        return """
                你是一个可复用的任务执行器。
                请严格遵守当前场景配置和输出结构配置，只返回符合要求的结果。

                场景编码：%s
                场景名称：%s
                场景说明：%s

                业务指令：
                %s
                """.formatted(
                scenario.code(),
                scenario.name(),
                scenario.description(),
                scenario.systemInstruction()).trim();
    }

    private String renderOutputSchema(LlmOutputSchema outputSchema, String fallbackOutputContract) {
        if (outputSchema == null) {
            if (isBlank(fallbackOutputContract)) {
                return "";
            }
            return """
                    输出结构配置：
                    %s
                    """.formatted(fallbackOutputContract).trim();
        }

        return joinPromptSections(
                """
                        输出结构配置（由 llm_output_schema 维护）：
                        schema_code：%s
                        schema_name：%s

                        目标JSON结构：
                        %s
                        """.formatted(
                        outputSchema.schemaCode(),
                        outputSchema.schemaName(),
                        outputSchema.schemaJson()),
                isBlank(outputSchema.fieldDescriptionJson()) ? "" : """
                        字段业务说明：
                        %s
                        """.formatted(outputSchema.fieldDescriptionJson()),
                isBlank(outputSchema.emptyValueRule()) ? "" : """
                        空值规则：
                        %s
                        """.formatted(outputSchema.emptyValueRule()));
    }

    private String renderExamples(List<FewShotExample> examples) {
        if (examples == null || examples.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder("""
                以下 Few-shot 示例由业务维护，只作为当前场景的补充样例。请学习输入和期望输出之间的映射关系，不要照抄示例中的姓名、订单号、日期、票号、PNR 等实体到新输入。

                业务 Few-shot 示例：
                """);
        for (int i = 0; i < examples.size(); i++) {
            FewShotExample example = examples.get(i);
            builder.append("示例 ").append(i + 1).append("：").append(example.title()).append('\n')
                    .append("输入：\n").append(example.input()).append('\n')
                    .append("期望输出：\n").append(example.expectedOutput()).append("\n\n");
        }
        return builder.toString().trim();
    }

    private String joinPromptSections(String... sections) {
        StringBuilder builder = new StringBuilder();
        for (String section : sections) {
            if (isBlank(section)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(section.trim());
        }
        return builder.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record FewShotRunResult(
            String scenarioCode,
            String scenarioName,
            String toolProfile,
            String input,
            String result) {
    }

    public record PromptPreview(
            String scenarioCode,
            String scenarioName,
            String systemPrompt,
            String userPrompt) {
    }

    public record PageResult<T>(
            List<T> items,
            long total,
            int page,
            int size,
            int totalPages) {

        public PageResult(List<T> items, long total, int page, int size) {
            this(items, total, page, size, (int) Math.ceil((double) total / size));
        }
    }
}
