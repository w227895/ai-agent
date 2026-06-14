package com.ke.deepseektools.prompt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
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
                SELECT id, scene_code, scene_name, description, is_active, create_time, update_time
                FROM llm_prompt_scene
                WHERE is_active = 1
                ORDER BY id
                """, this::mapRow);
    }

    public List<LlmPromptScenario> findAllScenarios() {
        return jdbcTemplate.query("""
                SELECT id, scene_code, scene_name, description, is_active, create_time, update_time
                FROM llm_prompt_scene
                ORDER BY is_active DESC, id
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
                    OR description LIKE ?)
                """,
                Long.class,
                searchTerm.raw(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like());
        List<LlmPromptScenario> items = jdbcTemplate.query("""
                SELECT id, scene_code, scene_name, description, is_active, create_time, update_time
                FROM llm_prompt_scene
                WHERE (? = ''
                    OR scene_code LIKE ?
                    OR scene_name LIKE ?
                    OR description LIKE ?)
                ORDER BY is_active DESC, id
                LIMIT ? OFFSET ?
                """,
                this::mapRow,
                searchTerm.raw(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                normalizedSize,
                offset);
        return new PageResult<>(items, total == null ? 0 : total, normalizedPage, normalizedSize);
    }

    public Optional<LlmPromptScenario> findById(long id) {
        return jdbcTemplate.query("""
                SELECT id, scene_code, scene_name, description, is_active, create_time, update_time
                FROM llm_prompt_scene
                WHERE id = ?
                """, this::mapRow, id).stream().findFirst();
    }

    public Optional<LlmPromptScenario> findByCode(String sceneCode) {
        return jdbcTemplate.query("""
                SELECT id, scene_code, scene_name, description, is_active, create_time, update_time
                FROM llm_prompt_scene
                WHERE scene_code = ?
                """, this::mapRow, trim(sceneCode)).stream().findFirst();
    }

    @Transactional
    public LlmPromptScenario save(LlmPromptScenario scenario) {
        LlmPromptScenario normalized = normalize(scenario);
        if (normalized.id() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO llm_prompt_scene
                            (scene_code, scene_name, description, is_active)
                        VALUES (?, ?, ?, ?)
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
                        description = ?,
                        is_active = ?
                    WHERE id = ?
                    """);
            bindScenario(statement, normalized);
            statement.setLong(5, normalized.id());
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
        return new PromptDictionaries.DictionaryResult(
                PromptDictionaries.codeTypes(),
                PromptDictionaries.templateTypes(),
                PromptDictionaries.mailTypes(),
                findAllScenarios());
    }

    private LlmPromptScenario mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new LlmPromptScenario(
                rs.getLong("id"),
                rs.getString("scene_code"),
                rs.getString("scene_name"),
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
        statement.setString(3, scenario.description());
        statement.setInt(4, scenario.active() ? 1 : 0);
    }

    private LlmPromptScenario normalize(LlmPromptScenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("场景不能为空");
        }
        String sceneCode = trim(scenario.sceneCode());
        String sceneName = trim(scenario.sceneName());
        if (sceneCode.isEmpty()) {
            throw new IllegalArgumentException("场景编码不能为空");
        }
        if (sceneName.isEmpty()) {
            throw new IllegalArgumentException("场景名称不能为空");
        }
        return new LlmPromptScenario(
                scenario.id(),
                sceneCode,
                sceneName,
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

    private record SearchTerm(String raw, String like) {
    }
}
