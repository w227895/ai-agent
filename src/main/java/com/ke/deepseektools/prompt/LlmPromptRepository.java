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
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        SearchTerm searchTerm = toSearchTerm(keyword);
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM llm_prompt
                WHERE (? = ''
                    OR prompt_code LIKE ?
                    OR code_type LIKE ?
                    OR template_type LIKE ?
                    OR system_prompt LIKE ?
                    OR user_prompt LIKE ?)
                """,
                Long.class,
                searchTerm.raw(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like());
        List<LlmPrompt> items = jdbcTemplate.query("""
                SELECT id, prompt_code, code_type, template_type, user_prompt, priority,
                       is_active, create_time, update_time, system_prompt, mail_type
                FROM llm_prompt
                WHERE (? = ''
                    OR prompt_code LIKE ?
                    OR code_type LIKE ?
                    OR template_type LIKE ?
                    OR system_prompt LIKE ?
                    OR user_prompt LIKE ?)
                ORDER BY priority ASC, update_time DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                this::mapRow,
                searchTerm.raw(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                normalizedSize,
                offset);
        return new PageResult<>(items, total == null ? 0 : total, normalizedPage, normalizedSize);
    }

    public List<LlmPrompt> findActivePrompts() {
        return jdbcTemplate.query("""
                SELECT id, prompt_code, code_type, template_type, user_prompt, priority,
                       is_active, create_time, update_time, system_prompt, mail_type
                FROM llm_prompt
                WHERE is_active = 1
                ORDER BY priority ASC, id DESC
                """, this::mapRow);
    }

    public Optional<LlmPrompt> findById(long id) {
        return jdbcTemplate.query("""
                SELECT id, prompt_code, code_type, template_type, user_prompt, priority,
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
                SELECT id, prompt_code, code_type, template_type, user_prompt, priority,
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
                            (prompt_code, code_type, template_type, user_prompt, priority,
                             is_active, system_prompt, mail_type)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, normalized.promptCode());
                statement.setString(2, normalized.codeType());
                statement.setString(3, normalized.templateType());
                statement.setString(4, normalized.userPrompt());
                statement.setInt(5, normalized.priority());
                statement.setInt(6, normalized.active() ? 1 : 0);
                statement.setString(7, normalized.systemPrompt());
                statement.setInt(8, normalized.mailType());
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            return findById(key == null ? 0 : key.longValue()).orElse(normalized);
        }

        int updated = jdbcTemplate.update("""
                UPDATE llm_prompt
                SET prompt_code = ?,
                    code_type = ?,
                    template_type = ?,
                    user_prompt = ?,
                    priority = ?,
                    is_active = ?,
                    system_prompt = ?,
                    mail_type = ?
                WHERE id = ?
                """,
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

    private LlmPrompt mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new LlmPrompt(
                rs.getLong("id"),
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
