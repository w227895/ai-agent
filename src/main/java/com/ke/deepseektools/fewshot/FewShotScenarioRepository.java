package com.ke.deepseektools.fewshot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FewShotScenarioRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final String DEFAULT_FLIGHT_CHANGE_PROMPT_CODE = "3131132944@qq.com";
    private static final String DEFAULT_FLIGHT_CHANGE_SCHEMA_CODE = "flight_change_extract";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FewShotScenarioRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void seedDefaults() {
        ensureSchemaCompatibility();
        seedDefaultMainPrompt();
        seedDefaultOutputSchema();
        migrateDefaultMainPromptToSchemaLayer();

        Optional<FewShotScenario> flightChangeMail = findByCode("flight-change-mail");
        if (flightChangeMail.isEmpty() || isLegacyFlightChangeScenario(flightChangeMail.get())) {
            save(seedFlightChangeMailScenario());
        }

        jdbcTemplate.update("""
                UPDATE few_shot_scenario
                SET prompt_code = ?,
                    schema_code = CASE WHEN schema_code IS NULL OR schema_code = '' THEN ? ELSE schema_code END
                WHERE code = 'flight-change-mail'
                  AND ((prompt_code IS NULL OR prompt_code = '') OR (schema_code IS NULL OR schema_code = ''))
                """, DEFAULT_FLIGHT_CHANGE_PROMPT_CODE, DEFAULT_FLIGHT_CHANGE_SCHEMA_CODE);
        migrateLegacyFlightChangeExample();
        removeDefaultCustomerIntentScenario();
    }

    public List<FewShotScenario> findAll() {
        return jdbcTemplate.query("""
                SELECT id, code, prompt_code, schema_code, name, description, input_label,
                       system_instruction, output_contract, tool_profile
                FROM few_shot_scenario
                ORDER BY code
                """, this::mapScenarioRow).stream()
                .map(row -> attachPromptLayers(row.scenario().withExamples(findExamples(row.id()))))
                .toList();
    }

    public Optional<FewShotScenario> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        return jdbcTemplate.query("""
                SELECT id, code, prompt_code, schema_code, name, description, input_label,
                       system_instruction, output_contract, tool_profile
                FROM few_shot_scenario
                WHERE code = ?
                """, this::mapScenarioRow, normalizeCode(code)).stream()
                .findFirst()
                .map(row -> attachPromptLayers(row.scenario().withExamples(findExamples(row.id()))));
    }

    @Transactional
    public FewShotScenario save(FewShotScenario scenario) {
        FewShotScenario normalized = normalizeScenario(scenario);
        jdbcTemplate.update("""
                INSERT INTO few_shot_scenario
                    (code, prompt_code, schema_code, name, description, input_label,
                     system_instruction, output_contract, tool_profile)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    prompt_code = VALUES(prompt_code),
                    schema_code = VALUES(schema_code),
                    name = VALUES(name),
                    description = VALUES(description),
                    input_label = VALUES(input_label),
                    system_instruction = VALUES(system_instruction),
                    output_contract = VALUES(output_contract),
                    tool_profile = VALUES(tool_profile)
                """,
                normalized.code(),
                normalized.promptCode(),
                normalized.schemaCode(),
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

    public Optional<LlmPromptTemplate> findActivePromptByCode(String promptCode) {
        if (promptCode == null || promptCode.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT prompt_code, code_type, template_type, user_prompt, priority,
                       is_active, system_prompt, mail_type
                FROM llm_prompt
                WHERE prompt_code = ? AND is_active = 1
                ORDER BY priority ASC, id DESC
                LIMIT 1
                """, this::mapPromptTemplate, promptCode.trim()).stream().findFirst();
    }

    public Optional<LlmOutputSchema> findActiveOutputSchemaByCode(String schemaCode) {
        if (schemaCode == null || schemaCode.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT schema_code, schema_name, schema_json, field_description_json,
                       empty_value_rule, is_active
                FROM llm_output_schema
                WHERE schema_code = ? AND is_active = 1
                LIMIT 1
                """, this::mapOutputSchema, schemaCode.trim()).stream().findFirst();
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

    private Optional<ScenarioRow> findScenarioRow(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT id, code, prompt_code, schema_code, name, description, input_label,
                       system_instruction, output_contract, tool_profile
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
                rs.getString("prompt_code"),
                null,
                rs.getString("schema_code"),
                null,
                List.of());
        return new ScenarioRow(rs.getLong("id"), scenario);
    }

    private LlmPromptTemplate mapPromptTemplate(ResultSet rs, int rowNum) throws SQLException {
        return new LlmPromptTemplate(
                rs.getString("prompt_code"),
                rs.getString("code_type"),
                rs.getString("template_type"),
                rs.getString("user_prompt"),
                rs.getInt("priority"),
                rs.getInt("is_active") == 1,
                rs.getString("system_prompt"),
                rs.getInt("mail_type"));
    }

    private LlmOutputSchema mapOutputSchema(ResultSet rs, int rowNum) throws SQLException {
        return new LlmOutputSchema(
                rs.getString("schema_code"),
                rs.getString("schema_name"),
                rs.getString("schema_json"),
                rs.getString("field_description_json"),
                rs.getString("empty_value_rule"),
                rs.getInt("is_active") == 1);
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
        String promptCode = blankToDefault(scenario.promptCode(), code);
        String schemaCode = blankToDefault(scenario.schemaCode(), code);
        List<FewShotExample> examples = scenario.examples() == null ? List.of()
                : scenario.examples().stream().map(this::normalizeExample).toList();

        return new FewShotScenario(
                code,
                blankToDefault(scenario.name(), code),
                blankToDefault(scenario.description(), ""),
                blankToDefault(scenario.inputLabel(), "邮件正文"),
                blankToDefault(scenario.systemInstruction(), ""),
                blankToDefault(scenario.outputContract(), ""),
                toolProfile,
                promptCode,
                scenario.mainPrompt(),
                schemaCode,
                scenario.outputSchema(),
                List.copyOf(examples));
    }

    private FewShotScenario attachPromptLayers(FewShotScenario scenario) {
        String promptCode = blankToDefault(scenario.promptCode(), scenario.code());
        String schemaCode = blankToDefault(scenario.schemaCode(), scenario.code());
        return scenario.withMainPrompt(findActivePromptByCode(promptCode).orElse(null))
                .withOutputSchema(findActiveOutputSchemaByCode(schemaCode).orElse(null));
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

    private void ensureSchemaCompatibility() {
        if (!columnExists("few_shot_scenario", "prompt_code")) {
            jdbcTemplate.execute("ALTER TABLE few_shot_scenario ADD COLUMN prompt_code VARCHAR(500) NOT NULL DEFAULT '' AFTER code");
        }
        if (!columnExists("few_shot_scenario", "schema_code")) {
            jdbcTemplate.execute("ALTER TABLE few_shot_scenario ADD COLUMN schema_code VARCHAR(128) NOT NULL DEFAULT '' AFTER prompt_code");
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS llm_output_schema (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    schema_code VARCHAR(128) NOT NULL DEFAULT '',
                    schema_name VARCHAR(255) NOT NULL DEFAULT '',
                    schema_json MEDIUMTEXT NOT NULL,
                    field_description_json MEDIUMTEXT NULL,
                    empty_value_rule TEXT NULL,
                    is_active TINYINT(4) NOT NULL DEFAULT 1,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_llm_output_schema_code (schema_code),
                    KEY idx_llm_output_schema_active (is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private boolean columnExists(String tableName, String columnName) {
        Boolean exists = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, columnName)) {
                return columns.next();
            }
        });
        return Boolean.TRUE.equals(exists);
    }

    private void seedDefaultMainPrompt() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_prompt WHERE prompt_code = ?",
                Integer.class,
                DEFAULT_FLIGHT_CHANGE_PROMPT_CODE);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO llm_prompt
                    (prompt_code, code_type, template_type, user_prompt, priority, is_active, system_prompt, mail_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                DEFAULT_FLIGHT_CHANGE_PROMPT_CODE,
                "2",
                "1",
                defaultFlightChangeUserPrompt(),
                1,
                1,
                defaultFlightChangeSystemPrompt(),
                0);
    }

    private void seedDefaultOutputSchema() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_output_schema WHERE schema_code = ?",
                Integer.class,
                DEFAULT_FLIGHT_CHANGE_SCHEMA_CODE);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO llm_output_schema
                    (schema_code, schema_name, schema_json, field_description_json, empty_value_rule, is_active)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                DEFAULT_FLIGHT_CHANGE_SCHEMA_CODE,
                "航变字段提取输出结构",
                flightChangeOutputContract(),
                flightChangeFieldDescriptions(),
                "缺失字段按字段类型返回空值：数组返回[]，字符串返回\"\"，对象返回{}，数值无法确定返回null。",
                1);
    }

    private void migrateDefaultMainPromptToSchemaLayer() {
        jdbcTemplate.update("""
                UPDATE llm_prompt
                SET system_prompt = ?
                WHERE prompt_code = ? AND system_prompt LIKE '%airLinePnrList%'
                """, defaultFlightChangeSystemPrompt(), DEFAULT_FLIGHT_CHANGE_PROMPT_CODE);
    }

    private String defaultFlightChangeUserPrompt() {
        return """
                解析航班变更邮件并返回结构化JSON数据：

                当前时间：{current_time}
                邮件内容：
                {email_content}

                请从邮件内容中提取航班变更信息，并映射到指定的JSON结构。只返回纯JSON数据，不要其他内容。
                """;
    }

    private String defaultFlightChangeSystemPrompt() {
        return """
                你是一个专业的邮件提取大师，专门从航班通知中提取航班变更信息。
                请严格按照输出结构配置返回纯JSON，不要任何Markdown格式或额外解释。
                出发地和到达地必须使用机场三字码形式。
                时间格式保持为yyyy-MM-dd HH:mm；如果没有时分，则保持为yyyy-MM-dd。
                【关键时区规则】出发时间使用出发地时区的日期，到达时间使用目的地时区的日期。长途航班自西向东飞行时，到达日期通常比出发日期晚一天。
                只基于邮件内容提取，不要编造邮件中不存在的信息。
                """;
    }

    private String flightChangeOutputContract() {
        return """
                {
                  "airLinePnrList": ["PNR1", "PNR2"],
                  "ticketNumList": ["ticket1", "ticket2"],
                  "pnrCode": "PNR_CODE",
                  "orderId": 123456,
                  "supplierOrderNum": "SUPPLIER_ORDER",
                  "originFlightChangeDetailList": [
                    {
                      "flightNum": "PG277",
                      "depTime": "2025-11-14 17:30",
                      "arrTime": "2025-11-14 19:05",
                      "depAirport": "BKK",
                      "arrAirport": "HKT",
                      "cabin": "N"
                    }
                  ],
                  "newFlightChangeDetailList": [
                    {
                      "flightNum": "PG277",
                      "depTime": "2025-11-14 18:20",
                      "arrTime": "2025-11-14 20:07",
                      "depAirport": "BKK",
                      "arrAirport": "HKT",
                      "cabin": "N"
                    }
                  ]
                }
                """;
    }

    private String flightChangeFieldDescriptions() {
        return """
                {
                  "airLinePnrList": "航司大编码列表。邮件中的“票号”如果实际是航司大编码，填充到这里。",
                  "ticketNumList": "真实客票票号列表。无法确认时返回空数组。",
                  "pnrCode": "PNR/预订编码。无法确认时返回空字符串。",
                  "orderId": "OMS订单号，转换为Long类型；无法确定时返回null。",
                  "supplierOrderNum": "供应商订单号。无法确定时返回空字符串。",
                  "originFlightChangeDetailList": "航变前航班明细列表。",
                  "newFlightChangeDetailList": "航变后航班明细列表。",
                  "flightNum": "航班号。",
                  "depTime": "出发时间，格式yyyy-MM-dd HH:mm；没有时分则yyyy-MM-dd。",
                  "arrTime": "到达时间，格式yyyy-MM-dd HH:mm；没有时分则yyyy-MM-dd。",
                  "depAirport": "出发机场三字码。",
                  "arrAirport": "到达机场三字码。",
                  "cabin": "舱位。无法确定时返回空字符串。"
                }
                """;
    }

    private FewShotScenario seedFlightChangeMailScenario() {
        return new FewShotScenario(
                "flight-change-mail",
                "航变邮件识别",
                "识别航司或供应商发来的航班变更邮件，并按生产 JSON 结构提取航变字段。",
                "邮件正文",
                "从航班变更邮件中提取航变前和航变后的航班明细，只返回纯 JSON。",
                flightChangeOutputContract(),
                FewShotScenario.NO_TOOLS,
                DEFAULT_FLIGHT_CHANGE_PROMPT_CODE,
                null,
                DEFAULT_FLIGHT_CHANGE_SCHEMA_CODE,
                null,
                List.of(new FewShotExample(
                        "flight-change-mail-001",
                        "标准航班时间变更",
                        defaultFlightChangeExampleInput(),
                        defaultFlightChangeExampleOutput(),
                        List.of("航变", "邮件识别"))));
    }

    private String defaultFlightChangeExampleInput() {
        return """
                Subject: 航班变更通知 MU5101

                订单号：123456
                尊敬的旅客张三：
                您预订的 2026-06-02 北京首都T2 至 上海虹桥T2 MU5101 航班因航空公司计划调整发生变更。
                原航班：MU5101，起飞时间 2026-06-02 08:00，到达时间 2026-06-02 10:10。
                新航班：MU5115，起飞时间 2026-06-02 10:30，到达时间 2026-06-02 12:40。
                票号：H8K2Q9，PNR：ABC123。
                请协助联系旅客确认是否接受变更。
                """;
    }

    private String defaultFlightChangeExampleOutput() {
        return """
                {
                  "airLinePnrList": ["H8K2Q9"],
                  "ticketNumList": [],
                  "pnrCode": "ABC123",
                  "orderId": 123456,
                  "supplierOrderNum": "",
                  "originFlightChangeDetailList": [
                    {
                      "flightNum": "MU5101",
                      "depTime": "2026-06-02 08:00",
                      "arrTime": "2026-06-02 10:10",
                      "depAirport": "PEK",
                      "arrAirport": "SHA",
                      "cabin": ""
                    }
                  ],
                  "newFlightChangeDetailList": [
                    {
                      "flightNum": "MU5115",
                      "depTime": "2026-06-02 10:30",
                      "arrTime": "2026-06-02 12:40",
                      "depAirport": "PEK",
                      "arrAirport": "SHA",
                      "cabin": ""
                    }
                  ]
                }
                """;
    }

    private void removeDefaultCustomerIntentScenario() {
        jdbcTemplate.update("""
                DELETE FROM few_shot_scenario
                WHERE code = 'customer-intent' AND name IN ('客户意图识别', '瀹㈡埛鎰忓浘璇嗗埆')
                """);
    }

    private void migrateLegacyFlightChangeExample() {
        findScenarioRow("flight-change-mail").ifPresent(row -> jdbcTemplate.update("""
                UPDATE few_shot_example
                SET title = ?, input_text = ?, expected_output = ?, tags_json = ?
                WHERE scenario_id = ? AND example_key = ? AND expected_output LIKE '%isFlightChangeMail%'
                """,
                "标准航班时间变更",
                defaultFlightChangeExampleInput(),
                defaultFlightChangeExampleOutput(),
                toJson(List.of("航变", "邮件识别")),
                row.id(),
                "flight-change-mail-001"));
    }

    private boolean isLegacyFlightChangeScenario(FewShotScenario scenario) {
        return "flight-change-workflow".equals(scenario.toolProfile())
                || containsIgnoreCase(scenario.systemInstruction(), "findRelatedOrder")
                || containsIgnoreCase(scenario.systemInstruction(), "createFlightChangeWorkOrder")
                || containsIgnoreCase(scenario.systemInstruction(), "notifyHumanAgent")
                || containsIgnoreCase(scenario.outputContract(), "isFlightChangeMail");
    }

    private boolean containsIgnoreCase(String value, String fragment) {
        return value != null && value.toLowerCase().contains(fragment.toLowerCase());
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

    private record ScenarioRow(long id, FewShotScenario scenario) {
    }

    public static class FewShotScenarioNotFoundException extends RuntimeException {

        public FewShotScenarioNotFoundException(String code) {
            super("Few-shot scenario not found: " + code);
        }
    }
}
