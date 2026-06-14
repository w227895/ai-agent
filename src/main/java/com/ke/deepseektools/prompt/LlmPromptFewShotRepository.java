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
public class LlmPromptFewShotRepository {

    private final JdbcTemplate jdbcTemplate;

    public LlmPromptFewShotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<LlmPromptFewShot> findPage(int page, int size, String keyword, Long promptId, Boolean active) {
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        SearchTerm searchTerm = toSearchTerm(keyword);
        Integer activeValue = active == null ? null : active ? 1 : 0;
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM llm_prompt_few_shot f
                JOIN llm_prompt p ON p.id = f.prompt_id
                WHERE (? = ''
                    OR f.title LIKE ?
                    OR f.content LIKE ?
                    OR p.prompt_code LIKE ?)
                  AND (? IS NULL OR f.prompt_id = ?)
                  AND (? IS NULL OR f.is_active = ?)
                """,
                Long.class,
                searchTerm.raw(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                promptId,
                promptId,
                activeValue,
                activeValue);
        long totalValue = total == null ? 0 : total;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalValue / normalizedSize));
        int normalizedPage = Math.min(Math.max(page, 1), totalPages);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<LlmPromptFewShot> items = jdbcTemplate.query("""
                SELECT f.id, f.prompt_id, f.title, f.content, f.sort_order, f.is_active, f.create_time, f.update_time
                FROM llm_prompt_few_shot f
                JOIN llm_prompt p ON p.id = f.prompt_id
                WHERE (? = ''
                    OR f.title LIKE ?
                    OR f.content LIKE ?
                    OR p.prompt_code LIKE ?)
                  AND (? IS NULL OR f.prompt_id = ?)
                  AND (? IS NULL OR f.is_active = ?)
                ORDER BY f.sort_order ASC, f.update_time DESC, f.id DESC
                LIMIT ? OFFSET ?
                """,
                this::mapRow,
                searchTerm.raw(),
                searchTerm.like(),
                searchTerm.like(),
                searchTerm.like(),
                promptId,
                promptId,
                activeValue,
                activeValue,
                normalizedSize,
                offset);
        return new PageResult<>(items, totalValue, normalizedPage, normalizedSize);
    }

    public List<LlmPromptFewShot> findActiveByPromptId(long promptId) {
        return jdbcTemplate.query("""
                SELECT id, prompt_id, title, content, sort_order, is_active, create_time, update_time
                FROM llm_prompt_few_shot
                WHERE prompt_id = ? AND is_active = 1
                ORDER BY sort_order ASC, id ASC
                """, this::mapRow, promptId);
    }

    public Optional<LlmPromptFewShot> findById(long id) {
        return jdbcTemplate.query("""
                SELECT id, prompt_id, title, content, sort_order, is_active, create_time, update_time
                FROM llm_prompt_few_shot
                WHERE id = ?
                """, this::mapRow, id).stream().findFirst();
    }

    @Transactional
    public LlmPromptFewShot save(LlmPromptFewShot fewShot) {
        LlmPromptFewShot normalized = normalize(fewShot);
        if (normalized.id() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO llm_prompt_few_shot
                            (prompt_id, title, content, sort_order, is_active)
                        VALUES (?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                bind(statement, normalized);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            return findById(key == null ? 0 : key.longValue()).orElse(normalized);
        }

        int updated = jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    UPDATE llm_prompt_few_shot
                    SET prompt_id = ?,
                        title = ?,
                        content = ?,
                        sort_order = ?,
                        is_active = ?
                    WHERE id = ?
                    """);
            bind(statement, normalized);
            statement.setLong(6, normalized.id());
            return statement;
        });
        if (updated == 0) {
            throw new IllegalArgumentException("Few-shot 不存在: " + normalized.id());
        }
        return findById(normalized.id()).orElse(normalized);
    }

    @Transactional
    public void setActive(long id, boolean active) {
        int updated = jdbcTemplate.update("UPDATE llm_prompt_few_shot SET is_active = ? WHERE id = ?",
                active ? 1 : 0, id);
        if (updated == 0) {
            throw new IllegalArgumentException("Few-shot 不存在: " + id);
        }
    }

    @Transactional
    public void delete(long id) {
        int deleted = jdbcTemplate.update("DELETE FROM llm_prompt_few_shot WHERE id = ?", id);
        if (deleted == 0) {
            throw new IllegalArgumentException("Few-shot 不存在: " + id);
        }
    }

    @Transactional
    public void deleteByPromptId(long promptId) {
        jdbcTemplate.update("DELETE FROM llm_prompt_few_shot WHERE prompt_id = ?", promptId);
    }

    private LlmPromptFewShot mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new LlmPromptFewShot(
                rs.getLong("id"),
                rs.getLong("prompt_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getInt("sort_order"),
                rs.getInt("is_active") == 1,
                toLocalDateTime(rs.getTimestamp("create_time")),
                toLocalDateTime(rs.getTimestamp("update_time")));
    }

    private LlmPromptFewShot normalize(LlmPromptFewShot fewShot) {
        if (fewShot == null || fewShot.promptId() == null) {
            throw new IllegalArgumentException("请选择提示词");
        }
        String content = trim(fewShot.content());
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Few-shot 内容不能为空");
        }
        return new LlmPromptFewShot(
                fewShot.id(),
                fewShot.promptId(),
                defaultValue(fewShot.title(), "Few-shot 示例"),
                content,
                fewShot.sortOrder(),
                fewShot.active(),
                fewShot.createTime(),
                fewShot.updateTime());
    }

    private void bind(java.sql.PreparedStatement statement, LlmPromptFewShot fewShot) throws SQLException {
        statement.setLong(1, fewShot.promptId());
        statement.setString(2, fewShot.title());
        statement.setString(3, fewShot.content());
        statement.setInt(4, fewShot.sortOrder());
        statement.setInt(5, fewShot.active() ? 1 : 0);
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

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record SearchTerm(String raw, String like) {
    }
}
