package com.ke.deepseektools.fewshot;

import java.util.List;

import com.ke.deepseektools.tools.FlightChangeWorkflowTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class FewShotPlatformService {

    private final ChatClient chatClient;
    private final FlightChangeWorkflowTools flightChangeWorkflowTools;
    private final FewShotScenarioRepository scenarioRepository;

    public FewShotPlatformService(ChatClient chatClient, FlightChangeWorkflowTools flightChangeWorkflowTools,
            FewShotScenarioRepository scenarioRepository) {
        this.chatClient = chatClient;
        this.flightChangeWorkflowTools = flightChangeWorkflowTools;
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

        ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                .system(buildSystemPrompt(scenario))
                .user("""
                        请按当前 few-shot 场景配置处理下面输入。

                        %s：
                        %s
                        """.formatted(scenario.inputLabel(), effectiveInput));

        if (FewShotScenario.FLIGHT_CHANGE_WORKFLOW.equals(scenario.toolProfile())) {
            request.tools(flightChangeWorkflowTools);
        }

        String output = request.call().content();
        return new FewShotRunResult(scenario.code(), scenario.name(), scenario.toolProfile(), effectiveInput, output);
    }

    private String buildSystemPrompt(FewShotScenario scenario) {
        return """
                你是一个可复用的 few-shot 任务执行器。你会收到一个业务场景配置、输出契约和若干示例。
                请优先遵守当前业务场景配置，其次参考示例中的表达方式和边界条件。
                不要把示例中的具体姓名、订单号、日期、票号等实体套用到新输入里。

                场景编码：%s
                场景名称：%s
                场景说明：%s

                业务指令：
                %s

                输出契约：
                %s

                Few-shot 示例：
                %s
                """.formatted(
                scenario.code(),
                scenario.name(),
                scenario.description(),
                scenario.systemInstruction(),
                scenario.outputContract(),
                renderExamples(scenario.examples()));
    }

    private String renderExamples(List<FewShotExample> examples) {
        if (examples == null || examples.isEmpty()) {
            return "无。";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < examples.size(); i++) {
            FewShotExample example = examples.get(i);
            builder.append("示例 ").append(i + 1).append("：").append(example.title()).append('\n')
                    .append("输入：\n").append(example.input()).append('\n')
                    .append("期望输出/处理方式：\n").append(example.expectedOutput()).append("\n\n");
        }
        return builder.toString().trim();
    }

    public record FewShotRunResult(
            String scenarioCode,
            String scenarioName,
            String toolProfile,
            String input,
            String result) {
    }
}
