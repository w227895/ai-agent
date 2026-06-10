package com.ke.deepseektools.fewshot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FewShotScenarioRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final String DEFAULT_FLIGHT_CHANGE_PROMPT_CODE = "3131132944@qq.com";
    private static final String DEFAULT_FLIGHT_CHANGE_SCHEMA_CODE = "flight_change_extract";
    private static final String DEFAULT_AFTER_SALES_PROMPT_CODE = "after-sales-support";
    private static final String DEFAULT_AFTER_SALES_SCHEMA_CODE = "after-sales-ticket-extract";

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
        seedDefaultAfterSalesPrompt();
        seedDefaultAfterSalesOutputSchema();
        migrateDefaultMainPromptToSchemaLayer();
        migrateLegacyScenarioTables();

        Optional<FewShotScenario> flightChangeMail = findByCode("flight-change-mail");
        if (flightChangeMail.isEmpty() || isLegacyFlightChangeScenario(flightChangeMail.get())) {
            save(seedFlightChangeMailScenario());
        }

        jdbcTemplate.update("""
                UPDATE llm_scenario
                SET prompt_id = (
                        SELECT id
                        FROM llm_prompt
                        WHERE prompt_code = ?
                        ORDER BY is_active DESC, priority ASC, id DESC
                        LIMIT 1
                    ),
                    schema_code = CASE WHEN schema_code IS NULL OR schema_code = '' THEN ? ELSE schema_code END
                WHERE scenario_code = 'flight-change-mail'
                """, DEFAULT_FLIGHT_CHANGE_PROMPT_CODE, DEFAULT_FLIGHT_CHANGE_SCHEMA_CODE);
        migrateLegacyFlightChangeExample();
        removeDefaultCustomerIntentScenario();

        if (findByCode("after-sales-service").isEmpty()) {
            save(seedAfterSalesScenario());
        }
    }

    public List<FewShotScenario> findAll() {
        return jdbcTemplate.query("""
                SELECT s.id, s.scenario_code AS code, s.prompt_id, p.prompt_code,
                       s.schema_code, s.scenario_name AS name, s.description, s.input_label,
                       s.system_instruction, s.output_contract, s.tool_profile
                FROM llm_scenario s
                JOIN llm_prompt p ON p.id = s.prompt_id
                WHERE s.is_active = 1
                ORDER BY s.scenario_code
                """, this::mapScenarioRow).stream()
                .map(this::attachPromptLayers)
                .toList();
    }

    public long count(String keyword) {
        SearchTerm searchTerm = toSearchTerm(keyword);
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM llm_scenario s
                JOIN llm_prompt p ON p.id = s.prompt_id
                WHERE (? = ''
                    OR s.scenario_code LIKE ?
                    OR s.scenario_name LIKE ?
                    OR s.description LIKE ?
                    OR p.prompt_code LIKE ?
                    OR s.schema_code LIKE ?)
                  AND s.is_active = 1
                """,
                Long.class,
                searchTerm.raw(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like());
        return total == null ? 0 : total;
    }

    public List<FewShotScenario> findPage(String keyword, int page, int size) {
        SearchTerm searchTerm = toSearchTerm(keyword);
        int offset = (Math.max(page, 1) - 1) * size;
        return jdbcTemplate.query("""
                SELECT s.id, s.scenario_code AS code, s.prompt_id, p.prompt_code,
                       s.schema_code, s.scenario_name AS name, s.description, s.input_label,
                       s.system_instruction, s.output_contract, s.tool_profile
                FROM llm_scenario s
                JOIN llm_prompt p ON p.id = s.prompt_id
                WHERE (? = ''
                    OR s.scenario_code LIKE ?
                    OR s.scenario_name LIKE ?
                    OR s.description LIKE ?
                    OR p.prompt_code LIKE ?
                    OR s.schema_code LIKE ?)
                  AND s.is_active = 1
                ORDER BY s.update_time DESC, s.scenario_code
                LIMIT ? OFFSET ?
                """,
                this::mapScenarioRow,
                searchTerm.raw(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                size,
                offset).stream()
                .map(this::attachPromptLayers)
                .toList();
    }

    public Optional<FewShotScenario> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        return jdbcTemplate.query("""
                SELECT s.id, s.scenario_code AS code, s.prompt_id, p.prompt_code,
                       s.schema_code, s.scenario_name AS name, s.description, s.input_label,
                       s.system_instruction, s.output_contract, s.tool_profile
                FROM llm_scenario s
                JOIN llm_prompt p ON p.id = s.prompt_id
                WHERE s.scenario_code = ? AND s.is_active = 1
                """, this::mapScenarioRow, normalizeCode(code)).stream()
                .findFirst()
                .map(this::attachPromptLayers);
    }

    @Transactional
    public FewShotScenario save(FewShotScenario scenario) {
        FewShotScenario normalized = normalizeScenario(scenario);
        long promptId = findPromptId(normalized.promptCode());
        jdbcTemplate.update("""
                INSERT INTO llm_scenario
                    (scenario_code, scenario_name, description, input_label, prompt_id, schema_code,
                     system_instruction, output_contract, tool_profile, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE
                    scenario_name = VALUES(scenario_name),
                    description = VALUES(description),
                    input_label = VALUES(input_label),
                    prompt_id = VALUES(prompt_id),
                    schema_code = VALUES(schema_code),
                    system_instruction = VALUES(system_instruction),
                    output_contract = VALUES(output_contract),
                    tool_profile = VALUES(tool_profile),
                    is_active = 1
                """,
                normalized.code(),
                normalized.name(),
                normalized.description(),
                normalized.inputLabel(),
                promptId,
                normalized.schemaCode(),
                normalized.systemInstruction(),
                normalized.outputContract(),
                normalized.toolProfile());

        jdbcTemplate.update("DELETE FROM llm_prompt_few_shot WHERE prompt_id = ?", promptId);
        insertExamples(promptId, normalized.examples());
        return findByCode(normalized.code()).orElse(normalized);
    }

    @Transactional
    public void deleteByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new FewShotScenarioNotFoundException("");
        }
        int deleted = jdbcTemplate.update("DELETE FROM llm_scenario WHERE scenario_code = ?", normalizeCode(code));
        if (deleted == 0) {
            throw new FewShotScenarioNotFoundException(code);
        }
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
                """, this::mapPromptTemplate, promptCode.trim()).stream()
                .findFirst()
                .map(prompt -> prompt.withExamples(findExamples(findPromptId(prompt.promptCode()))));
    }

    public List<LlmPromptTemplate> findAllPromptTemplates() {
        return jdbcTemplate.query("""
                SELECT prompt_code, code_type, template_type, user_prompt, priority,
                       is_active, system_prompt, mail_type
                FROM llm_prompt
                ORDER BY priority ASC, prompt_code
                """, this::mapPromptTemplate).stream()
                .map(prompt -> prompt.withExamples(findExamples(findPromptId(prompt.promptCode()))))
                .toList();
    }

    @Transactional
    public LlmPromptTemplate savePromptTemplate(LlmPromptTemplate promptTemplate) {
        LlmPromptTemplate normalized = normalizePromptTemplate(promptTemplate);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_prompt WHERE prompt_code = ?",
                Integer.class,
                normalized.promptCode());
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE llm_prompt
                    SET code_type = ?,
                        template_type = ?,
                        user_prompt = ?,
                        priority = ?,
                        is_active = ?,
                        system_prompt = ?,
                        mail_type = ?
                    WHERE prompt_code = ?
                    """,
                    normalized.codeType(),
                    normalized.templateType(),
                    normalized.userPrompt(),
                    normalized.priority(),
                    normalized.active() ? 1 : 0,
                    normalized.systemPrompt(),
                    normalized.mailType(),
                    normalized.promptCode());
        } else {
            jdbcTemplate.update("""
                    INSERT INTO llm_prompt
                        (prompt_code, code_type, template_type, user_prompt, priority, is_active, system_prompt, mail_type)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    normalized.promptCode(),
                    normalized.codeType(),
                    normalized.templateType(),
                    normalized.userPrompt(),
                    normalized.priority(),
                    normalized.active() ? 1 : 0,
                    normalized.systemPrompt(),
                    normalized.mailType());
        }
        if (promptTemplate.examples() != null) {
            long promptId = findPromptId(normalized.promptCode());
            jdbcTemplate.update("DELETE FROM llm_prompt_few_shot WHERE prompt_id = ?", promptId);
            insertExamples(promptId, normalized.examples());
        }
        return findActivePromptByCode(normalized.promptCode()).orElse(normalized);
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

    public List<LlmOutputSchema> findAllOutputSchemas() {
        return jdbcTemplate.query("""
                SELECT schema_code, schema_name, schema_json, field_description_json,
                       empty_value_rule, is_active
                FROM llm_output_schema
                ORDER BY schema_code
                """, this::mapOutputSchema);
    }

    @Transactional
    public LlmOutputSchema saveOutputSchema(LlmOutputSchema outputSchema) {
        LlmOutputSchema normalized = normalizeOutputSchema(outputSchema);
        jdbcTemplate.update("""
                INSERT INTO llm_output_schema
                    (schema_code, schema_name, schema_json, field_description_json, empty_value_rule, is_active)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    schema_name = VALUES(schema_name),
                    schema_json = VALUES(schema_json),
                    field_description_json = VALUES(field_description_json),
                    empty_value_rule = VALUES(empty_value_rule),
                    is_active = VALUES(is_active)
                """,
                normalized.schemaCode(),
                normalized.schemaName(),
                normalized.schemaJson(),
                normalized.fieldDescriptionJson(),
                normalized.emptyValueRule(),
                normalized.active() ? 1 : 0);
        return findActiveOutputSchemaByCode(normalized.schemaCode()).orElse(normalized);
    }

    @Transactional
    public FewShotScenario addExample(String scenarioCode, FewShotExample example) {
        ScenarioRow scenario = findScenarioRow(scenarioCode)
                .orElseThrow(() -> new FewShotScenarioNotFoundException(scenarioCode));
        FewShotExample normalized = normalizeExample(example);
        Integer nextSortOrder = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) + 1 FROM llm_prompt_few_shot WHERE prompt_id = ?",
                Integer.class,
                scenario.promptId());
        insertExample(scenario.promptId(), normalized, nextSortOrder == null ? 1 : nextSortOrder);
        return findByCode(scenario.scenario().code()).orElseThrow(() -> new FewShotScenarioNotFoundException(scenarioCode));
    }

    public List<FewShotFailureCase> findFailureCases(String scenarioCode) {
        return jdbcTemplate.query("""
                SELECT id, scenario_code, input_text, actual_output, expected_output, problem_note, created_at
                FROM few_shot_failure_case
                WHERE scenario_code = ?
                ORDER BY created_at DESC, id DESC
                """, this::mapFailureCase, normalizeCode(scenarioCode));
    }

    @Transactional
    public List<FewShotFailureCase> saveFailureCases(String scenarioCode, List<FewShotFailureCase> failureCases) {
        ScenarioRow scenario = findScenarioRow(scenarioCode)
                .orElseThrow(() -> new FewShotScenarioNotFoundException(scenarioCode));
        return failureCases.stream()
                .map(failureCase -> insertFailureCase(scenario.scenario().code(), normalizeFailureCase(failureCase)))
                .toList();
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

    private FewShotFailureCase insertFailureCase(String scenarioCode, FewShotFailureCase failureCase) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO few_shot_failure_case
                        (scenario_code, input_text, actual_output, expected_output, problem_note)
                    VALUES (?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, scenarioCode);
            statement.setString(2, failureCase.input());
            statement.setString(3, failureCase.actualOutput());
            statement.setString(4, failureCase.expectedOutput());
            statement.setString(5, failureCase.problemNote());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return new FewShotFailureCase(
                key == null ? null : key.longValue(),
                scenarioCode,
                failureCase.input(),
                failureCase.actualOutput(),
                failureCase.expectedOutput(),
                failureCase.problemNote(),
                LocalDateTime.now());
    }

    private Optional<ScenarioRow> findScenarioRow(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT s.id, s.scenario_code AS code, s.prompt_id, p.prompt_code,
                       s.schema_code, s.scenario_name AS name, s.description, s.input_label,
                       s.system_instruction, s.output_contract, s.tool_profile
                FROM llm_scenario s
                JOIN llm_prompt p ON p.id = s.prompt_id
                WHERE s.scenario_code = ? AND s.is_active = 1
                """, this::mapScenarioRow, normalizeCode(code)).stream().findFirst();
    }

    private long findPromptId(String promptCode) {
        Long id = jdbcTemplate.query("""
                SELECT id
                FROM llm_prompt
                WHERE prompt_code = ?
                ORDER BY is_active DESC, priority ASC, id DESC
                LIMIT 1
                """, rs -> rs.next() ? rs.getLong("id") : null, promptCode);
        if (id == null) {
            throw new IllegalArgumentException("llm_prompt not found: " + promptCode);
        }
        return id;
    }

    private void insertExamples(long promptId, List<FewShotExample> examples) {
        for (int i = 0; i < examples.size(); i++) {
            insertExample(promptId, examples.get(i), i + 1);
        }
    }

    private void insertExample(long promptId, FewShotExample example, int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO llm_prompt_few_shot
                    (prompt_id, example_key, title, input_text, expected_output, tags_json, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    title = VALUES(title),
                    input_text = VALUES(input_text),
                    expected_output = VALUES(expected_output),
                    tags_json = VALUES(tags_json),
                    sort_order = VALUES(sort_order),
                    enabled = 1
                """,
                promptId,
                example.id(),
                example.title(),
                example.input(),
                example.expectedOutput(),
                toJson(example.tags()),
                sortOrder);
    }

    private List<FewShotExample> findExamples(long promptId) {
        return jdbcTemplate.query("""
                SELECT example_key, title, input_text, expected_output, tags_json
                FROM llm_prompt_few_shot
                WHERE prompt_id = ? AND enabled = 1
                ORDER BY sort_order, id
                """, this::mapExample, promptId);
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
        return new ScenarioRow(rs.getLong("id"), rs.getLong("prompt_id"), scenario);
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
                rs.getInt("mail_type"),
                List.of());
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

    private FewShotFailureCase mapFailureCase(ResultSet rs, int rowNum) throws SQLException {
        var createdAt = rs.getTimestamp("created_at");
        return new FewShotFailureCase(
                rs.getLong("id"),
                rs.getString("scenario_code"),
                rs.getString("input_text"),
                rs.getString("actual_output"),
                rs.getString("expected_output"),
                rs.getString("problem_note"),
                createdAt == null ? null : createdAt.toLocalDateTime());
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

    private FewShotScenario attachPromptLayers(ScenarioRow row) {
        FewShotScenario scenario = row.scenario();
        String schemaCode = blankToDefault(scenario.schemaCode(), scenario.code());
        List<FewShotExample> examples = findExamples(row.promptId());
        LlmPromptTemplate prompt = findPromptById(row.promptId())
                .map(item -> item.withExamples(examples))
                .orElse(null);
        return scenario.withExamples(examples)
                .withMainPrompt(prompt)
                .withOutputSchema(findActiveOutputSchemaByCode(schemaCode).orElse(null));
    }

    private Optional<LlmPromptTemplate> findPromptById(long promptId) {
        return jdbcTemplate.query("""
                SELECT prompt_code, code_type, template_type, user_prompt, priority,
                       is_active, system_prompt, mail_type
                FROM llm_prompt
                WHERE id = ?
                """, this::mapPromptTemplate, promptId).stream().findFirst();
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

    private FewShotFailureCase normalizeFailureCase(FewShotFailureCase failureCase) {
        return new FewShotFailureCase(
                failureCase.id(),
                blankToDefault(failureCase.scenarioCode(), ""),
                blankToDefault(failureCase.input(), ""),
                blankToDefault(failureCase.actualOutput(), ""),
                blankToDefault(failureCase.expectedOutput(), ""),
                blankToDefault(failureCase.problemNote(), ""),
                failureCase.createdAt());
    }

    private LlmPromptTemplate normalizePromptTemplate(LlmPromptTemplate promptTemplate) {
        String promptCode = blankToDefault(promptTemplate.promptCode(), "");
        if (promptCode.isBlank()) {
            throw new IllegalArgumentException("prompt_code cannot be blank");
        }
        return new LlmPromptTemplate(
                promptCode,
                blankToDefault(promptTemplate.codeType(), "2"),
                blankToDefault(promptTemplate.templateType(), "1"),
                blankToDefault(promptTemplate.userPrompt(), ""),
                promptTemplate.priority(),
                promptTemplate.active(),
                blankToDefault(promptTemplate.systemPrompt(), ""),
                promptTemplate.mailType(),
                promptTemplate.examples() == null ? null
                        : promptTemplate.examples().stream().map(this::normalizeExample).toList());
    }

    private LlmOutputSchema normalizeOutputSchema(LlmOutputSchema outputSchema) {
        String schemaCode = normalizeCode(blankToDefault(outputSchema.schemaCode(), ""));
        if (schemaCode.isBlank()) {
            throw new IllegalArgumentException("schema_code cannot be blank");
        }
        return new LlmOutputSchema(
                schemaCode,
                blankToDefault(outputSchema.schemaName(), schemaCode),
                blankToDefault(outputSchema.schemaJson(), "{}"),
                blankToDefault(outputSchema.fieldDescriptionJson(), ""),
                blankToDefault(outputSchema.emptyValueRule(), ""),
                outputSchema.active());
    }

    private void ensureSchemaCompatibility() {
        if (tableExists("few_shot_scenario") && !columnExists("few_shot_scenario", "prompt_code")) {
            jdbcTemplate.execute("ALTER TABLE few_shot_scenario ADD COLUMN prompt_code VARCHAR(500) NOT NULL DEFAULT '' AFTER code");
        }
        if (tableExists("few_shot_scenario") && !columnExists("few_shot_scenario", "schema_code")) {
            jdbcTemplate.execute("ALTER TABLE few_shot_scenario ADD COLUMN schema_code VARCHAR(128) NOT NULL DEFAULT '' AFTER prompt_code");
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS llm_scenario (
                    id BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT,
                    scenario_code VARCHAR(128) NOT NULL DEFAULT '',
                    scenario_name VARCHAR(255) NOT NULL DEFAULT '',
                    description TEXT NULL,
                    input_label VARCHAR(128) NOT NULL DEFAULT '邮件正文',
                    prompt_id BIGINT(20) UNSIGNED NOT NULL,
                    schema_code VARCHAR(128) NOT NULL DEFAULT '',
                    system_instruction TEXT NULL,
                    output_contract TEXT NULL,
                    tool_profile VARCHAR(128) NOT NULL DEFAULT 'none',
                    is_active TINYINT(4) NOT NULL DEFAULT 1,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_llm_scenario_code (scenario_code),
                    KEY idx_llm_scenario_prompt_id (prompt_id),
                    KEY idx_llm_scenario_active (is_active),
                    CONSTRAINT fk_llm_scenario_prompt
                        FOREIGN KEY (prompt_id) REFERENCES llm_prompt (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS llm_prompt_few_shot (
                    id BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT,
                    prompt_id BIGINT(20) UNSIGNED NOT NULL,
                    example_key VARCHAR(128) NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    input_text MEDIUMTEXT NOT NULL,
                    expected_output MEDIUMTEXT NOT NULL,
                    tags_json TEXT NULL,
                    sort_order INT NOT NULL DEFAULT 0,
                    enabled TINYINT(1) NOT NULL DEFAULT 1,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_llm_prompt_few_shot_key (prompt_id, example_key),
                    KEY idx_llm_prompt_few_shot_prompt (prompt_id, sort_order),
                    CONSTRAINT fk_llm_prompt_few_shot_prompt
                        FOREIGN KEY (prompt_id) REFERENCES llm_prompt (id)
                        ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
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
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS few_shot_failure_case (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    scenario_code VARCHAR(128) NOT NULL,
                    input_text MEDIUMTEXT NOT NULL,
                    actual_output MEDIUMTEXT NULL,
                    expected_output MEDIUMTEXT NOT NULL,
                    problem_note TEXT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_few_shot_failure_case_scenario_created (scenario_code, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void migrateLegacyScenarioTables() {
        if (!tableExists("few_shot_scenario")) {
            return;
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO llm_scenario
                    (scenario_code, scenario_name, description, input_label, prompt_id, schema_code,
                     system_instruction, output_contract, tool_profile, is_active)
                SELECT s.code, s.name, s.description, s.input_label,
                       (
                           SELECT p.id
                           FROM llm_prompt p
                           WHERE p.prompt_code = s.prompt_code
                           ORDER BY p.is_active DESC, p.priority ASC, p.id DESC
                           LIMIT 1
                       ),
                       s.schema_code, s.system_instruction, s.output_contract, s.tool_profile, 1
                FROM few_shot_scenario s
                WHERE EXISTS (
                    SELECT 1 FROM llm_prompt p WHERE p.prompt_code = s.prompt_code
                )
                """);
        if (!tableExists("few_shot_example")) {
            return;
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO llm_prompt_few_shot
                    (prompt_id, example_key, title, input_text, expected_output, tags_json,
                     sort_order, enabled, created_at, updated_at)
                SELECT ns.prompt_id, e.example_key, e.title, e.input_text, e.expected_output,
                       e.tags_json, e.sort_order, e.enabled, e.created_at, e.updated_at
                FROM few_shot_example e
                JOIN few_shot_scenario os ON os.id = e.scenario_id
                JOIN llm_scenario ns ON ns.scenario_code = os.code
                """);
    }

    private boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            try (ResultSet tables = connection.getMetaData().getTables(
                    connection.getCatalog(), null, tableName, new String[] {"TABLE"})) {
                return tables.next();
            }
        });
        return Boolean.TRUE.equals(exists);
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

    private void seedDefaultAfterSalesPrompt() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_prompt WHERE prompt_code = ?",
                Integer.class,
                DEFAULT_AFTER_SALES_PROMPT_CODE);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO llm_prompt
                    (prompt_code, code_type, template_type, user_prompt, priority, is_active, system_prompt, mail_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                DEFAULT_AFTER_SALES_PROMPT_CODE,
                "after-sale",
                "1",
                defaultAfterSalesUserPrompt(),
                10,
                1,
                defaultAfterSalesSystemPrompt(),
                2);
    }

    private void seedDefaultAfterSalesOutputSchema() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_output_schema WHERE schema_code = ?",
                Integer.class,
                DEFAULT_AFTER_SALES_SCHEMA_CODE);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO llm_output_schema
                    (schema_code, schema_name, schema_json, field_description_json, empty_value_rule, is_active)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                DEFAULT_AFTER_SALES_SCHEMA_CODE,
                "售后工单识别输出结构",
                afterSalesOutputContract(),
                afterSalesFieldDescriptions(),
                "缺失字段按类型返回空值：字符串返回\"\"，数组返回[]，对象返回{}，金额和时间无法确定时返回null。不要根据经验补充输入中不存在的信息。",
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

    private String defaultAfterSalesUserPrompt() {
        return """
                解析售后诉求文本并返回结构化JSON数据：

                当前时间：{current_time}
                售后文本：
                {email_content}

                请从售后文本中识别客户诉求、订单信息、处理优先级、期望动作和需要人工跟进的风险点。
                只返回纯JSON数据，不要任何解释、Markdown或额外文本。
                """;
    }

    private String defaultAfterSalesSystemPrompt() {
        return """
                你是一个售后工单识别助手，负责把客户邮件、IM记录或客服备注整理为标准售后工单。
                必须严格按照输出结构返回纯JSON，不要补充输入中不存在的事实。
                requestType 必须从 refund、change、invoice、complaint、baggage、other 中选择。
                urgency 必须从 low、medium、high、critical 中选择；涉及当天出行、客诉升级、金额争议或航班临近时提高紧急程度。
                如果文本同时包含多个诉求，以客户最明确、最急迫的诉求作为 primaryRequest，并把其他诉求放入 secondaryRequests。
                对金额、订单号、票号、联系方式等信息只基于原文提取，无法确认时返回空值。
                """;
    }

    private String afterSalesOutputContract() {
        return """
                {
                  "requestType": "refund",
                  "urgency": "high",
                  "primaryRequest": "客户要求退票退款",
                  "secondaryRequests": ["补开发票"],
                  "customer": {
                    "name": "张三",
                    "contact": "13800000000"
                  },
                  "order": {
                    "orderId": 123456,
                    "supplierOrderNum": "SUP-20260601001",
                    "pnrCode": "ABC123",
                    "ticketNumList": ["7811234567890"]
                  },
                  "flightInfo": {
                    "flightNum": "MU5101",
                    "depDate": "2026-06-08",
                    "route": "PEK-SHA"
                  },
                  "requestedAction": "申请非自愿退票并退回原支付账户",
                  "evidenceList": ["航司取消通知", "客户付款截图"],
                  "refundAmount": 1280.50,
                  "riskTags": ["金额争议", "客户催办"],
                  "needManualFollowUp": true,
                  "summary": "客户因航班取消要求非自愿退票退款，并催促当天处理。"
                }
                """;
    }

    private String afterSalesFieldDescriptions() {
        return """
                {
                  "requestType": "售后诉求类型：refund退票/退款，change改期/改签，invoice发票，complaint投诉，baggage行李，other其他。",
                  "urgency": "处理紧急程度：low、medium、high、critical。",
                  "primaryRequest": "客户最主要、最急迫的诉求。",
                  "secondaryRequests": "同一文本里的次要诉求列表。",
                  "customer": "客户信息；没有明确姓名或联系方式时返回空字符串。",
                  "order": "订单与票务信息；无法提取的字段返回空值。",
                  "flightInfo": "航班相关信息；不涉及航班时返回空对象字段。",
                  "requestedAction": "客户希望平台或客服执行的动作。",
                  "evidenceList": "客户或客服提到的证明材料列表。",
                  "refundAmount": "明确提到的退款或争议金额；没有金额时返回null。",
                  "riskTags": "风险标签，例如客户催办、金额争议、投诉升级、当天出行、材料缺失。",
                  "needManualFollowUp": "是否需要人工跟进；有投诉、金额争议、规则不明确、缺材料或临近出行时为true。",
                  "summary": "一句话概括售后工单。"
                }
                """;
    }

    private FewShotScenario seedAfterSalesScenario() {
        return new FewShotScenario(
                "after-sales-service",
                "售后工单识别",
                "识别客户售后邮件、IM记录或客服备注，提取诉求类型、紧急程度、订单信息、处理动作和风险标签。",
                "售后诉求文本",
                "从售后文本中提取客户诉求、订单票务信息、期望动作和人工跟进风险，只返回纯 JSON。",
                afterSalesOutputContract(),
                FewShotScenario.NO_TOOLS,
                DEFAULT_AFTER_SALES_PROMPT_CODE,
                null,
                DEFAULT_AFTER_SALES_SCHEMA_CODE,
                null,
                List.of(
                        new FewShotExample(
                                "after-sales-001",
                                "航班取消要求非自愿退票",
                                afterSalesRefundExampleInput(),
                                afterSalesRefundExampleOutput(),
                                List.of("售后", "退票", "航班取消")),
                        new FewShotExample(
                                "after-sales-002",
                                "客户投诉改签差价",
                                afterSalesComplaintExampleInput(),
                                afterSalesComplaintExampleOutput(),
                                List.of("售后", "投诉", "改签")),
                        new FewShotExample(
                                "after-sales-003",
                                "发票补开与行程单诉求",
                                afterSalesInvoiceExampleInput(),
                                afterSalesInvoiceExampleOutput(),
                                List.of("售后", "发票", "行程单"))));
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

    private String afterSalesRefundExampleInput() {
        return """
                客户微信留言：
                订单号 202606080001，旅客李娜，手机号 13900001111。
                原定 2026-06-08 CA1831 北京首都T3 飞 上海虹桥，PNR Q7K9LM，票号 9991234567890。
                航司今天通知航班取消，客户不接受改期，要求按非自愿退票全额退款 1280.50 元，退回原支付账户。
                客户已经催了两次，说今天 18 点前不处理就投诉。
                附件：航司取消短信截图、支付截图。
                """;
    }

    private String afterSalesRefundExampleOutput() {
        return """
                {
                  "requestType": "refund",
                  "urgency": "high",
                  "primaryRequest": "客户要求按非自愿退票全额退款",
                  "secondaryRequests": [],
                  "customer": {
                    "name": "李娜",
                    "contact": "13900001111"
                  },
                  "order": {
                    "orderId": 202606080001,
                    "supplierOrderNum": "",
                    "pnrCode": "Q7K9LM",
                    "ticketNumList": ["9991234567890"]
                  },
                  "flightInfo": {
                    "flightNum": "CA1831",
                    "depDate": "2026-06-08",
                    "route": "PEK-SHA"
                  },
                  "requestedAction": "按非自愿退票规则全额退款并退回原支付账户",
                  "evidenceList": ["航司取消短信截图", "支付截图"],
                  "refundAmount": 1280.50,
                  "riskTags": ["客户催办", "投诉风险", "航班取消"],
                  "needManualFollowUp": true,
                  "summary": "客户因CA1831航班取消要求非自愿退票全额退款，并要求当天18点前处理。"
                }
                """;
    }

    private String afterSalesComplaintExampleInput() {
        return """
                客服备注：
                用户王先生反馈订单 202606070088，供应商单号 SUP-778899，PNR M5N8Q2。
                原本买的是 6月12日 MU2158 西安-上海，客户昨天申请改到 6月13日同航线。
                页面显示差价 320 元，但支付后实际扣了 520 元。客户认为多扣 200 元，要求退回差价并解释原因。
                客户语气比较激动，表示如果今晚不给答复就去平台投诉。
                未提供扣款截图，已让客户补充。
                """;
    }

    private String afterSalesComplaintExampleOutput() {
        return """
                {
                  "requestType": "complaint",
                  "urgency": "high",
                  "primaryRequest": "客户投诉改签差价扣款金额不一致并要求退回多扣金额",
                  "secondaryRequests": ["解释扣款原因"],
                  "customer": {
                    "name": "王先生",
                    "contact": ""
                  },
                  "order": {
                    "orderId": 202606070088,
                    "supplierOrderNum": "SUP-778899",
                    "pnrCode": "M5N8Q2",
                    "ticketNumList": []
                  },
                  "flightInfo": {
                    "flightNum": "MU2158",
                    "depDate": "2026-06-12",
                    "route": "XIY-SHA"
                  },
                  "requestedAction": "核实改签差价扣款明细，退回客户认为多扣的200元并说明原因",
                  "evidenceList": [],
                  "refundAmount": 200,
                  "riskTags": ["金额争议", "投诉风险", "材料缺失"],
                  "needManualFollowUp": true,
                  "summary": "客户投诉改签差价展示320元但实际扣款520元，要求退回200元并解释原因。"
                }
                """;
    }

    private String afterSalesInvoiceExampleInput() {
        return """
                邮件主题：补开发票和行程单

                你好，我司同事赵敏上周出差订单号 202606010045，手机号 13622223333。
                行程是 2026-06-03 CZ3102 广州到北京，票号 7844567890123。
                当时忘记开票了，请补开电子发票，抬头：北京星河科技有限公司，税号 91110108MA0000000X。
                另外也请把电子行程单一起发到 finance@example.com，谢谢。
                """;
    }

    private String afterSalesInvoiceExampleOutput() {
        return """
                {
                  "requestType": "invoice",
                  "urgency": "medium",
                  "primaryRequest": "客户要求补开电子发票",
                  "secondaryRequests": ["发送电子行程单"],
                  "customer": {
                    "name": "赵敏",
                    "contact": "13622223333"
                  },
                  "order": {
                    "orderId": 202606010045,
                    "supplierOrderNum": "",
                    "pnrCode": "",
                    "ticketNumList": ["7844567890123"]
                  },
                  "flightInfo": {
                    "flightNum": "CZ3102",
                    "depDate": "2026-06-03",
                    "route": "CAN-PEK"
                  },
                  "requestedAction": "补开电子发票并将电子行程单发送到finance@example.com",
                  "evidenceList": [],
                  "refundAmount": null,
                  "riskTags": [],
                  "needManualFollowUp": false,
                  "summary": "客户为订单202606010045补开电子发票，并要求发送电子行程单。"
                }
                """;
    }

    private void removeDefaultCustomerIntentScenario() {
        jdbcTemplate.update("""
                DELETE FROM llm_scenario
                WHERE scenario_code = 'customer-intent'
                  AND scenario_name IN ('客户意图识别', '瀹㈡埛鎰忓浘璇嗗埆')
                """);
    }

    private void migrateLegacyFlightChangeExample() {
        findScenarioRow("flight-change-mail").ifPresent(row -> jdbcTemplate.update("""
                UPDATE llm_prompt_few_shot
                SET title = ?, input_text = ?, expected_output = ?, tags_json = ?
                WHERE prompt_id = ? AND example_key = ? AND expected_output LIKE '%isFlightChangeMail%'
                """,
                "标准航班时间变更",
                defaultFlightChangeExampleInput(),
                defaultFlightChangeExampleOutput(),
                toJson(List.of("航变", "邮件识别")),
                row.promptId(),
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

    private SearchTerm toSearchTerm(String keyword) {
        String raw = keyword == null ? "" : keyword.trim();
        return new SearchTerm(raw, "%" + raw + "%");
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

    private record ScenarioRow(long id, long promptId, FewShotScenario scenario) {
    }

    private record SearchTerm(String raw, String like) {
    }

    public static class FewShotScenarioNotFoundException extends RuntimeException {

        public FewShotScenarioNotFoundException(String code) {
            super("Few-shot scenario not found: " + code);
        }
    }
}
