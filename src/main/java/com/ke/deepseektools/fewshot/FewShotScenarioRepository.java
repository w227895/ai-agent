package com.ke.deepseektools.fewshot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FewShotScenarioRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FewShotScenarioRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void seedDefaults() {
        Optional<FewShotScenario> flightChangeMail = findByCode("flight-change-mail");
        if (flightChangeMail.isEmpty() || isLegacyToolBasedFlightChangeScenario(flightChangeMail.get())) {
            save(seedFlightChangeMailScenario());
        }
        removeDefaultCustomerIntentScenario();
    }

    public List<FewShotScenario> findAll() {
        return jdbcTemplate.query("""
                SELECT id, code, name, description, input_label, system_instruction, output_contract, tool_profile
                FROM few_shot_scenario
                ORDER BY code
                """, this::mapScenarioRow).stream()
                .map(row -> row.scenario().withExamples(findExamples(row.id())))
                .toList();
    }

    public Optional<FewShotScenario> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        List<ScenarioRow> rows = jdbcTemplate.query("""
                SELECT id, code, name, description, input_label, system_instruction, output_contract, tool_profile
                FROM few_shot_scenario
                WHERE code = ?
                """, this::mapScenarioRow, normalizeCode(code));

        return rows.stream()
                .findFirst()
                .map(row -> row.scenario().withExamples(findExamples(row.id())));
    }

    @Transactional
    public FewShotScenario save(FewShotScenario scenario) {
        FewShotScenario normalized = normalizeScenario(scenario);
        jdbcTemplate.update("""
                INSERT INTO few_shot_scenario
                    (code, name, description, input_label, system_instruction, output_contract, tool_profile)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    description = VALUES(description),
                    input_label = VALUES(input_label),
                    system_instruction = VALUES(system_instruction),
                    output_contract = VALUES(output_contract),
                    tool_profile = VALUES(tool_profile)
                """,
                normalized.code(),
                normalized.name(),
                normalized.description(),
                normalized.inputLabel(),
                normalized.systemInstruction(),
                normalized.outputContract(),
                normalized.toolProfile());

        long scenarioId = findScenarioId(normalized.code());
        jdbcTemplate.update("DELETE FROM few_shot_example WHERE scenario_id = ?", scenarioId);
        insertExamples(scenarioId, normalized.examples());
        return findByCode(normalized.code()).orElse(normalized);
    }

    @Transactional
    public FewShotScenario addExample(String scenarioCode, FewShotExample example) {
        ScenarioRow scenario = findScenarioRow(scenarioCode)
                .orElseThrow(() -> new FewShotScenarioNotFoundException(scenarioCode));
        FewShotExample normalized = normalizeExample(example);
        Integer nextSortOrder = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) + 1 FROM few_shot_example WHERE scenario_id = ?",
                Integer.class,
                scenario.id());
        insertExample(scenario.id(), normalized, nextSortOrder == null ? 1 : nextSortOrder);
        return findByCode(scenario.scenario().code()).orElseThrow(() -> new FewShotScenarioNotFoundException(scenarioCode));
    }

    public void recordRun(FewShotScenario scenario, String input, String output, String status, String errorMessage,
            long elapsedMs) {
        jdbcTemplate.update("""
                INSERT INTO few_shot_run_log
                    (scenario_code, scenario_name, tool_profile, input_text, output_text, status, error_message, elapsed_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                scenario.code(),
                scenario.name(),
                scenario.toolProfile(),
                input == null ? "" : input,
                output,
                status,
                errorMessage,
                elapsedMs);
    }

    private boolean isLegacyToolBasedFlightChangeScenario(FewShotScenario scenario) {
        return "flight-change-workflow".equals(scenario.toolProfile())
                || containsIgnoreCase(scenario.systemInstruction(), "findRelatedOrder")
                || containsIgnoreCase(scenario.systemInstruction(), "createFlightChangeWorkOrder")
                || containsIgnoreCase(scenario.systemInstruction(), "notifyHumanAgent");
    }

    private void removeDefaultCustomerIntentScenario() {
        jdbcTemplate.update("""
                DELETE FROM few_shot_scenario
                WHERE code = 'customer-intent' AND name = '客户意图识别'
                """);
    }

    private boolean containsIgnoreCase(String value, String fragment) {
        return value != null && value.toLowerCase().contains(fragment.toLowerCase());
    }

    private Optional<ScenarioRow> findScenarioRow(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT id, code, name, description, input_label, system_instruction, output_contract, tool_profile
                FROM few_shot_scenario
                WHERE code = ?
                """, this::mapScenarioRow, normalizeCode(code)).stream().findFirst();
    }

    private long findScenarioId(String code) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM few_shot_scenario WHERE code = ?", Long.class, code);
        if (id == null) {
            throw new FewShotScenarioNotFoundException(code);
        }
        return id;
    }

    private void insertExamples(long scenarioId, List<FewShotExample> examples) {
        for (int i = 0; i < examples.size(); i++) {
            insertExample(scenarioId, examples.get(i), i + 1);
        }
    }

    private void insertExample(long scenarioId, FewShotExample example, int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO few_shot_example
                    (scenario_id, example_key, title, input_text, expected_output, tags_json, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    title = VALUES(title),
                    input_text = VALUES(input_text),
                    expected_output = VALUES(expected_output),
                    tags_json = VALUES(tags_json),
                    sort_order = VALUES(sort_order),
                    enabled = 1
                """,
                scenarioId,
                example.id(),
                example.title(),
                example.input(),
                example.expectedOutput(),
                toJson(example.tags()),
                sortOrder);
    }

    private List<FewShotExample> findExamples(long scenarioId) {
        return jdbcTemplate.query("""
                SELECT example_key, title, input_text, expected_output, tags_json
                FROM few_shot_example
                WHERE scenario_id = ? AND enabled = 1
                ORDER BY sort_order, id
                """, this::mapExample, scenarioId);
    }

    private ScenarioRow mapScenarioRow(ResultSet rs, int rowNum) throws SQLException {
        FewShotScenario scenario = new FewShotScenario(
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("input_label"),
                rs.getString("system_instruction"),
                rs.getString("output_contract"),
                rs.getString("tool_profile"),
                List.of());
        return new ScenarioRow(rs.getLong("id"), scenario);
    }

    private FewShotExample mapExample(ResultSet rs, int rowNum) throws SQLException {
        return new FewShotExample(
                rs.getString("example_key"),
                rs.getString("title"),
                rs.getString("input_text"),
                rs.getString("expected_output"),
                fromJson(rs.getString("tags_json")));
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

    private String toJson(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags == null ? List.of() : tags);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private FewShotScenario seedFlightChangeMailScenario() {
        return new FewShotScenario(
                "flight-change-mail",
                "航变邮件识别",
                "识别航司或供应商发来的航班变更邮件，并提取关键字段生成结构化结果。",
                "邮件正文",
                """
                        你是航变邮件识别助手，负责判断输入是否为航空公司或供应商发来的航班变更邮件。
                        如果是航变邮件，请从正文中提取乘机人、原航班、新航班、出发日期、起飞时间、航线、票号、PNR/预订编码、变更原因、影响说明和需要人工确认的问题。
                        如果不是航变邮件，请明确说明不是航变邮件，并简要给出原因。
                        只基于输入内容判断和提取，不要编造订单、工单、通知结果或输入中不存在的信息。
                        字段缺失时统一写“未识别”。
                        """,
                """
                        用中文输出 JSON，不要包裹 Markdown 代码块。字段如下：
                        {
                          "isFlightChangeMail": true/false,
                          "reason": "识别依据",
                          "fields": {
                            "passengerName": "乘机人或未识别",
                            "ticketNo": "票号或未识别",
                            "bookingCode": "PNR/预订编码或未识别",
                            "originalFlightNo": "原航班号或未识别",
                            "newFlightNo": "新航班号或未识别",
                            "departureDate": "yyyy-MM-dd 或未识别",
                            "originalDepartureTime": "原起飞时间或未识别",
                            "newDepartureTime": "新起飞时间或未识别",
                            "route": "航线或未识别",
                            "changeReason": "变更原因或未识别",
                            "impact": "影响说明或未识别"
                          },
                          "manualAttention": ["需要人工关注的问题"]
                        }
                        """,
                FewShotScenario.NO_TOOLS,
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
                                {
                                  "isFlightChangeMail": true,
                                  "reason": "邮件标题和正文都说明 MU5101 航班发生变更，并给出了新航班和时间。",
                                  "fields": {
                                    "passengerName": "张三",
                                    "ticketNo": "781-1234567890",
                                    "bookingCode": "H8K2Q9",
                                    "originalFlightNo": "MU5101",
                                    "newFlightNo": "MU5115",
                                    "departureDate": "2026-06-02",
                                    "originalDepartureTime": "2026-06-02 08:00",
                                    "newDepartureTime": "2026-06-02 10:30",
                                    "route": "北京首都T2-上海虹桥T2",
                                    "changeReason": "航空公司计划调整",
                                    "impact": "起飞时间由 08:00 变更为 10:30"
                                  },
                                  "manualAttention": ["需要联系旅客确认是否接受变更"]
                                }
                                """,
                        List.of("航变", "邮件识别"))));
    }

    private record ScenarioRow(long id, FewShotScenario scenario) {
    }

    public static class FewShotScenarioNotFoundException extends RuntimeException {

        public FewShotScenarioNotFoundException(String code) {
            super("Few-shot scenario not found: " + code);
        }
    }
}
