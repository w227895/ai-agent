package com.ke.deepseektools.prompt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LlmPromptScenarioRepository {

    private final JdbcTemplate jdbcTemplate;

    public LlmPromptScenarioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LlmPromptScenario> findActiveScenarios() {
        return jdbcTemplate.query("""
                SELECT id, scene_code, scene_name, code_type, code_type_name, template_type, template_type_name,
                       mail_type, mail_type_name, description, is_active, create_time, update_time
                FROM llm_prompt_scene
                WHERE is_active = 1
                ORDER BY code_type, template_type, mail_type, scene_code, id
                """, this::mapRow);
    }

    public List<LlmPromptScenario> findAllScenarios() {
        return jdbcTemplate.query("""
                SELECT id, scene_code, scene_name, code_type, code_type_name, template_type, template_type_name,
                       mail_type, mail_type_name, description, is_active, create_time, update_time
                FROM llm_prompt_scene
                ORDER BY code_type, template_type, mail_type, scene_code, id
                """, this::mapRow);
    }

    public PageResult<LlmPromptScenario> findPage(int page, int size, String keyword) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        SearchTerm searchTerm = toSearchTerm(keyword);
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM llm_prompt_scene
                WHERE (? = ''
                    OR scene_code LIKE ?
                    OR scene_name LIKE ?
                    OR code_type_name LIKE ?
                    OR template_type_name LIKE ?
                    OR mail_type_name LIKE ?
                    OR description LIKE ?)
                """,
                Long.class,
                searchTerm.raw(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like());
        List<LlmPromptScenario> items = jdbcTemplate.query("""
                SELECT id, scene_code, scene_name, code_type, code_type_name, template_type, template_type_name,
                       mail_type, mail_type_name, description, is_active, create_time, update_time
                FROM llm_prompt_scene
                WHERE (? = ''
                    OR scene_code LIKE ?
                    OR scene_name LIKE ?
                    OR code_type_name LIKE ?
                    OR template_type_name LIKE ?
                    OR mail_type_name LIKE ?
                    OR description LIKE ?)
                ORDER BY update_time DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                this::mapRow,
                searchTerm.raw(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                normalizedSize,
                offset);
        return new PageResult<>(items, total == null ? 0 : total, normalizedPage, normalizedSize);
    }

    public Optional<LlmPromptScenario> findById(long id) {
        return jdbcTemplate.query("""
                SELECT id, scene_code, scene_name, code_type, code_type_name, template_type, template_type_name,
                       mail_type, mail_type_name, description, is_active, create_time, update_time
                FROM llm_prompt_scene
                WHERE id = ?
                """, this::mapRow, id).stream().findFirst();
    }

    @Transactional
    public LlmPromptScenario save(LlmPromptScenario scenario) {
        LlmPromptScenario normalized = normalize(scenario);
        if (normalized.id() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO llm_prompt_scene
                            (scene_code, scene_name, code_type, code_type_name, template_type, template_type_name,
                             mail_type, mail_type_name, description, is_active)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                bindScenario(statement, normalized);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            return findById(key == null ? 0 : key.longValue()).orElse(normalized);
        }

        int updated = jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    UPDATE llm_prompt_scene
                    SET scene_code = ?,
                        scene_name = ?,
                        code_type = ?,
                        code_type_name = ?,
                        template_type = ?,
                        template_type_name = ?,
                        mail_type = ?,
                        mail_type_name = ?,
                        description = ?,
                        is_active = ?
                    WHERE id = ?
                    """);
            bindScenario(statement, normalized);
            statement.setLong(11, normalized.id());
            return statement;
        });
        if (updated == 0) {
            throw new IllegalArgumentException("场景不存在: " + normalized.id());
        }
        return findById(normalized.id()).orElse(normalized);
    }

    @Transactional
    public void setActive(long id, boolean active) {
        int updated = jdbcTemplate.update("UPDATE llm_prompt_scene SET is_active = ? WHERE id = ?", active ? 1 : 0, id);
        if (updated == 0) {
            throw new IllegalArgumentException("场景不存在: " + id);
        }
    }

    @Transactional
    public void delete(long id) {
        int deleted = jdbcTemplate.update("DELETE FROM llm_prompt_scene WHERE id = ?", id);
        if (deleted == 0) {
            throw new IllegalArgumentException("场景不存在: " + id);
        }
    }

    public PromptDictionaries.DictionaryResult dictionaries() {
        List<LlmPromptScenario> scenarios = findAllScenarios();
        return new PromptDictionaries.DictionaryResult(
                distinct(scenarios, DictionaryKind.CODE_TYPE),
                distinct(scenarios, DictionaryKind.TEMPLATE_TYPE),
                distinct(scenarios, DictionaryKind.MAIL_TYPE),
                scenarios);
    }

    private List<PromptDictionaries.DictionaryItem> distinct(List<LlmPromptScenario> scenarios, DictionaryKind kind) {
        Map<String, String> items = new LinkedHashMap<>();
        for (LlmPromptScenario scenario : scenarios) {
            switch (kind) {
                case CODE_TYPE -> items.putIfAbsent(scenario.codeType(), scenario.codeTypeName());
                case TEMPLATE_TYPE -> items.putIfAbsent(scenario.templateType(), scenario.templateTypeName());
                case MAIL_TYPE -> items.putIfAbsent(String.valueOf(scenario.mailType()), scenario.mailTypeName());
            }
        }
        return items.entrySet().stream()
                .map(entry -> new PromptDictionaries.DictionaryItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private LlmPromptScenario mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new LlmPromptScenario(
                rs.getLong("id"),
                rs.getString("scene_code"),
                rs.getString("scene_name"),
                rs.getString("code_type"),
                rs.getString("code_type_name"),
                rs.getString("template_type"),
                rs.getString("template_type_name"),
                rs.getInt("mail_type"),
                rs.getString("mail_type_name"),
                rs.getString("description"),
                rs.getInt("is_active") == 1,
                toLocalDateTime(rs.getTimestamp("create_time")),
                toLocalDateTime(rs.getTimestamp("update_time")));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private void bindScenario(java.sql.PreparedStatement statement, LlmPromptScenario scenario) throws SQLException {
        statement.setString(1, scenario.sceneCode());
        statement.setString(2, scenario.sceneName());
        statement.setString(3, scenario.codeType());
        statement.setString(4, scenario.codeTypeName());
        statement.setString(5, scenario.templateType());
        statement.setString(6, scenario.templateTypeName());
        statement.setInt(7, scenario.mailType());
        statement.setString(8, scenario.mailTypeName());
        statement.setString(9, scenario.description());
        statement.setInt(10, scenario.active() ? 1 : 0);
    }

    private LlmPromptScenario normalize(LlmPromptScenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("场景不能为空");
        }
        String sceneCode = trim(scenario.sceneCode());
        String sceneName = trim(scenario.sceneName());
        String codeType = trim(scenario.codeType());
        String codeTypeName = trim(scenario.codeTypeName());
        String templateType = trim(scenario.templateType());
        String templateTypeName = trim(scenario.templateTypeName());
        String mailTypeName = trim(scenario.mailTypeName());
        if (sceneCode.isEmpty()) {
            throw new IllegalArgumentException("场景编码不能为空");
        }
        if (sceneName.isEmpty()) {
            throw new IllegalArgumentException("场景名称不能为空");
        }
        if (codeType.isEmpty() || codeTypeName.isEmpty()) {
            throw new IllegalArgumentException("代码类型和值名称不能为空");
        }
        if (templateType.isEmpty() || templateTypeName.isEmpty()) {
            throw new IllegalArgumentException("模板类型和值名称不能为空");
        }
        if (mailTypeName.isEmpty()) {
            throw new IllegalArgumentException("邮件类型名称不能为空");
        }
        return new LlmPromptScenario(
                scenario.id(),
                sceneCode,
                sceneName,
                codeType,
                codeTypeName,
                templateType,
                templateTypeName,
                scenario.mailType(),
                mailTypeName,
                blankToEmpty(scenario.description()),
                scenario.active(),
                scenario.createTime(),
                scenario.updateTime());
    }

    private SearchTerm toSearchTerm(String keyword) {
        String raw = trim(keyword);
        return new SearchTerm(raw, "%" + raw + "%");
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private enum DictionaryKind {
        CODE_TYPE,
        TEMPLATE_TYPE,
        MAIL_TYPE
    }

    private record SearchTerm(String raw, String like) {
    }
}
