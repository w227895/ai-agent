package com.ke.deepseektools.fewshot;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class FewShotPlatformService {

    private static final DateTimeFormatter PROMPT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatClient chatClient;
    private final FewShotScenarioRepository scenarioRepository;

    public FewShotPlatformService(ChatClient chatClient, FewShotScenarioRepository scenarioRepository) {
        this.chatClient = chatClient;
        this.scenarioRepository = scenarioRepository;
    }

    public List<FewShotScenario> listScenarios() {
        return scenarioRepository.findAll();
    }

    public FewShotScenario getScenario(String code) {
        return scenarioRepository.findByCode(code)
                .orElseThrow(() -> new FewShotScenarioRepository.FewShotScenarioNotFoundException(code));
    }

    public FewShotScenario saveScenario(FewShotScenario scenario) {
        return scenarioRepository.save(scenario);
    }

    public FewShotScenario addExample(String scenarioCode, FewShotExample example) {
        return scenarioRepository.addExample(scenarioCode, example);
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
}
