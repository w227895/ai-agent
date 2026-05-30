package com.ke.deepseektools.fewshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class FewShotScenarioRepository {

    private final Map<String, FewShotScenario> scenarios = new ConcurrentHashMap<>();

    public FewShotScenarioRepository() {
        save(seedFlightChangeMailScenario());
        save(seedCustomerIntentScenario());
    }

    public List<FewShotScenario> findAll() {
        return scenarios.values().stream()
                .sorted(Comparator.comparing(FewShotScenario::code))
                .toList();
    }

    public Optional<FewShotScenario> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(scenarios.get(normalizeCode(code)));
    }

    public FewShotScenario save(FewShotScenario scenario) {
        FewShotScenario normalized = normalizeScenario(scenario);
        scenarios.put(normalized.code(), normalized);
        return normalized;
    }

    public FewShotScenario addExample(String scenarioCode, FewShotExample example) {
        FewShotScenario scenario = findByCode(scenarioCode)
                .orElseThrow(() -> new FewShotScenarioNotFoundException(scenarioCode));

        List<FewShotExample> examples = new ArrayList<>(scenario.examples());
        examples.add(normalizeExample(example));
        return save(scenario.withExamples(examples));
    }

    private FewShotScenario normalizeScenario(FewShotScenario scenario) {
        String code = normalizeCode(scenario.code());
        String toolProfile = blankToDefault(scenario.toolProfile(), FewShotScenario.NO_TOOLS);
        List<FewShotExample> examples = scenario.examples() == null ? List.of()
                : scenario.examples().stream().map(this::normalizeExample).toList();

        return new FewShotScenario(
                code,
                blankToDefault(scenario.name(), code),
                blankToDefault(scenario.description(), ""),
                blankToDefault(scenario.inputLabel(), "输入内容"),
                blankToDefault(scenario.systemInstruction(), ""),
                blankToDefault(scenario.outputContract(), ""),
                toolProfile,
                List.copyOf(examples));
    }

    private FewShotExample normalizeExample(FewShotExample example) {
        String id = blankToDefault(example.id(), "example-" + System.currentTimeMillis());
        List<String> tags = example.tags() == null ? List.of() : List.copyOf(example.tags());
        return new FewShotExample(
                id,
                blankToDefault(example.title(), id),
                blankToDefault(example.input(), ""),
                blankToDefault(example.expectedOutput(), ""),
                tags);
    }

    private String normalizeCode(String code) {
        return code.trim().toLowerCase().replace('_', '-');
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private FewShotScenario seedFlightChangeMailScenario() {
        return new FewShotScenario(
                "flight-change-mail",
                "航变邮件处理",
                "识别航司或供应商发来的航班变更邮件，提取关键字段并触发订单、工单和人工通知工具链。",
                "邮件正文",
                """
                        你是航变邮件处理助手，负责处理航空公司或供应商发来的航班变更邮件。
                        工作流必须按顺序执行：
                        1. 识别输入是否为航变邮件。
                        2. 从正文提取乘机人、原航班、新航班、日期、航线、票号、PNR/预订编码、变更原因、影响说明。
                        3. 如果是航变邮件，必须调用 findRelatedOrder 关联订单。
                        4. 关联订单后，必须调用 createFlightChangeWorkOrder 生成工单。
                        5. 工单生成后，必须调用 notifyHumanAgent 通知人工处理。
                        如果字段缺失，不要编造；写“未识别”，并在工单和通知里说明。
                        """,
                """
                        最终用中文输出处理摘要，包含：
                        - 识别结果
                        - 提取字段
                        - 订单匹配结果
                        - 工单号
                        - 通知结果
                        - 需要人工关注的问题
                        """,
                FewShotScenario.FLIGHT_CHANGE_WORKFLOW,
                List.of(new FewShotExample(
                        "flight-change-mail-001",
                        "标准航班时间变更",
                        """
                                Subject: 航班变更通知 MU5101

                                尊敬的旅客张三：
                                您预订的 2026-06-02 北京首都T2 至 上海虹桥T2 MU5101 航班因航空公司计划调整发生变更。
                                原航班：MU5101，起飞时间 2026-06-02 08:00。
                                新航班：MU5115，起飞时间 2026-06-02 10:30。
                                票号：781-1234567890，PNR：H8K2Q9。
                                请协助联系旅客确认是否接受变更。
                                """,
                        """
                                这是航变邮件。应提取张三、票号 781-1234567890、PNR H8K2Q9、原航班 MU5101、新航班 MU5115、日期 2026-06-02、航线北京首都T2-上海虹桥T2，并按顺序调用订单关联、工单创建和人工通知工具。
                                """,
                        List.of("航变", "工单"))));
    }

    private FewShotScenario seedCustomerIntentScenario() {
        return new FewShotScenario(
                "customer-intent",
                "客户意图识别",
                "不依赖业务工具的通用文本分类示例，用来证明平台可以承载非航变场景。",
                "客户原文",
                """
                        你是客户意图识别助手。请根据 few-shot 示例判断用户表达的主要意图。
                        只基于输入内容判断，不要补充输入中不存在的信息。
                        """,
                """
                        用 JSON 输出：
                        {
                          "intent": "退款|改签|咨询|投诉|其他",
                          "confidence": 0.0 到 1.0,
                          "reason": "一句中文理由"
                        }
                        """,
                FewShotScenario.NO_TOOLS,
                List.of(
                        new FewShotExample(
                                "customer-intent-001",
                                "退款诉求",
                                "我这张票临时不去了，想问一下能不能退，手续费是多少？",
                                "{\"intent\":\"退款\",\"confidence\":0.88,\"reason\":\"用户明确询问退票和手续费。\"}",
                                List.of("分类")),
                        new FewShotExample(
                                "customer-intent-002",
                                "投诉诉求",
                                "客服一直没有回复，导致我错过处理时间，这个问题必须给我一个说法。",
                                "{\"intent\":\"投诉\",\"confidence\":0.82,\"reason\":\"用户表达对服务响应的不满并要求处理。\"}",
                                List.of("分类"))));
    }

    public static class FewShotScenarioNotFoundException extends RuntimeException {

        public FewShotScenarioNotFoundException(String code) {
            super("Few-shot scenario not found: " + code);
        }
    }
}
