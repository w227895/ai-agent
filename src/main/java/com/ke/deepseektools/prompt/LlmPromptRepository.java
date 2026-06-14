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
public class LlmPromptRepository {

    private final JdbcTemplate jdbcTemplate;

    public LlmPromptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<LlmPrompt> findPage(int page, int size, String keyword) {
        return findPage(page, size, keyword, null, null);
    }

    public PageResult<LlmPrompt> findPage(int page, int size, String keyword, Long sceneId, Boolean active) {
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        SearchTerm searchTerm = toSearchTerm(keyword);
        Integer activeValue = active == null ? null : active ? 1 : 0;
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM llm_prompt p
                LEFT JOIN llm_prompt_scene s ON s.id = p.scene_id
                WHERE (? = ''
                    OR p.prompt_code LIKE ?
                    OR p.code_type LIKE ?
                    OR p.template_type LIKE ?
                    OR p.system_prompt LIKE ?
                    OR p.user_prompt LIKE ?
                    OR s.scene_code LIKE ?
                    OR s.scene_name LIKE ?)
                  AND (? IS NULL OR p.scene_id = ?)
                  AND (? IS NULL OR p.is_active = ?)
                """,
                Long.class,
                searchTerm.raw(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                sceneId,
                sceneId,
                activeValue,
                activeValue);
        long totalValue = total == null ? 0 : total;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalValue / normalizedSize));
        int normalizedPage = Math.min(Math.max(page, 1), totalPages);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<LlmPrompt> items = jdbcTemplate.query("""
                SELECT p.id, p.scene_id, p.output_schema_id, p.prompt_code, p.code_type, p.template_type, p.user_prompt, p.priority,
                       p.is_active, p.create_time, p.update_time, p.system_prompt, p.mail_type
                FROM llm_prompt p
                LEFT JOIN llm_prompt_scene s ON s.id = p.scene_id
                WHERE (? = ''
                    OR p.prompt_code LIKE ?
                    OR p.code_type LIKE ?
                    OR p.template_type LIKE ?
                    OR p.system_prompt LIKE ?
                    OR p.user_prompt LIKE ?
                    OR s.scene_code LIKE ?
                    OR s.scene_name LIKE ?)
                  AND (? IS NULL OR p.scene_id = ?)
                  AND (? IS NULL OR p.is_active = ?)
                ORDER BY p.priority ASC, p.update_time DESC, p.id DESC
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
                searchTerm.like(),
                sceneId,
                sceneId,
                activeValue,
                activeValue,
                normalizedSize,
                offset);
        return new PageResult<>(items, totalValue, normalizedPage, normalizedSize);
    }

    public List<LlmPrompt> findActivePrompts() {
        return jdbcTemplate.query("""
                SELECT id, scene_id, output_schema_id, prompt_code, code_type, template_type, user_prompt, priority,
                       is_active, create_time, update_time, system_prompt, mail_type
                FROM llm_prompt
                WHERE is_active = 1
                ORDER BY priority ASC, id DESC
                """, this::mapRow);
    }

    public Optional<LlmPrompt> findById(long id) {
        return jdbcTemplate.query("""
                SELECT id, scene_id, output_schema_id, prompt_code, code_type, template_type, user_prompt, priority,
                       is_active, create_time, update_time, system_prompt, mail_type
                FROM llm_prompt
                WHERE id = ?
                """, this::mapRow, id).stream().findFirst();
    }

    public Optional<LlmPrompt> findActiveByCode(String promptCode) {
        if (promptCode == null || promptCode.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT id, scene_id, output_schema_id, prompt_code, code_type, template_type, user_prompt, priority,
                       is_active, create_time, update_time, system_prompt, mail_type
                FROM llm_prompt
                WHERE prompt_code = ? AND is_active = 1
                ORDER BY priority ASC, id DESC
                LIMIT 1
                """, this::mapRow, promptCode.trim()).stream().findFirst();
    }

    @Transactional
    public LlmPrompt save(LlmPrompt prompt) {
        LlmPrompt normalized = normalize(prompt);
        if (normalized.id() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO llm_prompt
                            (scene_id, output_schema_id, prompt_code, code_type, template_type, user_prompt, priority,
                             is_active, system_prompt, mail_type)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                setNullableLong(statement, 1, normalized.sceneId());
                setNullableLong(statement, 2, normalized.outputSchemaId());
                statement.setString(3, normalized.promptCode());
                statement.setString(4, normalized.codeType());
                statement.setString(5, normalized.templateType());
                statement.setString(6, normalized.userPrompt());
                statement.setInt(7, normalized.priority());
                statement.setInt(8, normalized.active() ? 1 : 0);
                statement.setString(9, normalized.systemPrompt());
                statement.setInt(10, normalized.mailType());
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            return findById(key == null ? 0 : key.longValue()).orElse(normalized);
        }

        int updated = jdbcTemplate.update("""
                UPDATE llm_prompt
                SET scene_id = ?,
                    output_schema_id = ?,
                    prompt_code = ?,
                    code_type = ?,
                    template_type = ?,
                    user_prompt = ?,
                    priority = ?,
                    is_active = ?,
                    system_prompt = ?,
                    mail_type = ?
                WHERE id = ?
                """,
                normalized.sceneId(),
                normalized.outputSchemaId(),
                normalized.promptCode(),
                normalized.codeType(),
                normalized.templateType(),
                normalized.userPrompt(),
                normalized.priority(),
                normalized.active() ? 1 : 0,
                normalized.systemPrompt(),
                normalized.mailType(),
                normalized.id());
        if (updated == 0) {
            throw new IllegalArgumentException("提示词不存在: " + normalized.id());
        }
        return findById(normalized.id()).orElse(normalized);
    }

    @Transactional
    public void delete(long id) {
        int deleted = jdbcTemplate.update("DELETE FROM llm_prompt WHERE id = ?", id);
        if (deleted == 0) {
            throw new IllegalArgumentException("提示词不存在: " + id);
        }
    }

    @Transactional
    public void setActive(long id, boolean active) {
        int updated = jdbcTemplate.update("UPDATE llm_prompt SET is_active = ? WHERE id = ?", active ? 1 : 0, id);
        if (updated == 0) {
            throw new IllegalArgumentException("提示词不存在: " + id);
        }
    }

    public long countBySceneId(long sceneId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_prompt WHERE scene_id = ?",
                Long.class,
                sceneId);
        return total == null ? 0 : total;
    }

    private LlmPrompt mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new LlmPrompt(
                rs.getLong("id"),
                nullableLong(rs, "scene_id"),
                nullableLong(rs, "output_schema_id"),
                rs.getString("prompt_code"),
                rs.getString("code_type"),
                rs.getString("template_type"),
                rs.getString("user_prompt"),
                rs.getInt("priority"),
                rs.getInt("is_active") == 1,
                toLocalDateTime(rs.getTimestamp("create_time")),
                toLocalDateTime(rs.getTimestamp("update_time")),
                rs.getString("system_prompt"),
                rs.getInt("mail_type"));
    }

    private LlmPrompt normalize(LlmPrompt prompt) {
        if (prompt == null) {
            throw new IllegalArgumentException("提示词不能为空");
        }
        String promptCode = trim(prompt.promptCode());
        if (promptCode.isEmpty()) {
            throw new IllegalArgumentException("代码不能为空");
        }
        return new LlmPrompt(
                prompt.id(),
                prompt.sceneId(),
                prompt.outputSchemaId(),
                promptCode,
                defaultValue(prompt.codeType(), "2"),
                defaultValue(prompt.templateType(), "1"),
                blankToEmpty(prompt.userPrompt()),
                prompt.priority(),
                prompt.active(),
                prompt.createTime(),
                prompt.updateTime(),
                blankToEmpty(prompt.systemPrompt()),
                prompt.mailType());
    }

    private void setNullableLong(java.sql.PreparedStatement statement, int parameterIndex, Long value)
            throws SQLException {
        if (value == null) {
            statement.setNull(parameterIndex, java.sql.Types.BIGINT);
        } else {
            statement.setLong(parameterIndex, value);
        }
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private SearchTerm toSearchTerm(String keyword) {
        String raw = trim(keyword);
        return new SearchTerm(raw, "%" + raw + "%");
    }

    private String defaultValue(String value, String defaultValue) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? defaultValue : trimmed;
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
