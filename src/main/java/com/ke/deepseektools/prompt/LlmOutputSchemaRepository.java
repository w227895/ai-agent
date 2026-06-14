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
public class LlmOutputSchemaRepository {

    private final JdbcTemplate jdbcTemplate;

    public LlmOutputSchemaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<LlmOutputSchema> findPage(int page, int size, String keyword, Long sceneId, Boolean active) {
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        SearchTerm searchTerm = toSearchTerm(keyword);
        Integer activeValue = active == null ? null : active ? 1 : 0;
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM llm_output_schema os
                LEFT JOIN llm_prompt_scene s ON s.id = os.scene_id
                WHERE (? = ''
                    OR os.schema_code LIKE ?
                    OR os.schema_name LIKE ?
                    OR os.description LIKE ?
                    OR os.schema_content LIKE ?
                    OR os.prompt_fragment LIKE ?
                    OR s.scene_code LIKE ?
                    OR s.scene_name LIKE ?)
                  AND (? IS NULL OR os.scene_id = ?)
                  AND (? IS NULL OR os.is_active = ?)
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
        List<LlmOutputSchema> items = jdbcTemplate.query("""
                SELECT os.id, os.schema_code, os.schema_name, os.scene_id, os.schema_content, os.prompt_fragment,
                       os.sample_output, os.description, os.is_active, os.create_time, os.update_time
                FROM llm_output_schema os
                LEFT JOIN llm_prompt_scene s ON s.id = os.scene_id
                WHERE (? = ''
                    OR os.schema_code LIKE ?
                    OR os.schema_name LIKE ?
                    OR os.description LIKE ?
                    OR os.schema_content LIKE ?
                    OR os.prompt_fragment LIKE ?
                    OR s.scene_code LIKE ?
                    OR s.scene_name LIKE ?)
                  AND (? IS NULL OR os.scene_id = ?)
                  AND (? IS NULL OR os.is_active = ?)
                ORDER BY os.is_active DESC, os.update_time DESC, os.id DESC
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

    public List<LlmOutputSchema> findActiveSchemas() {
        return jdbcTemplate.query("""
                SELECT id, schema_code, schema_name, scene_id, schema_content, prompt_fragment, sample_output,
                       description, is_active, create_time, update_time
                FROM llm_output_schema
                WHERE is_active = 1
                ORDER BY scene_id IS NULL, scene_id, schema_code
                """, this::mapRow);
    }

    public Optional<LlmOutputSchema> findById(long id) {
        return jdbcTemplate.query("""
                SELECT id, schema_code, schema_name, scene_id, schema_content, prompt_fragment, sample_output,
                       description, is_active, create_time, update_time
                FROM llm_output_schema
                WHERE id = ?
                """, this::mapRow, id).stream().findFirst();
    }

    public Optional<LlmOutputSchema> findByCode(String schemaCode) {
        if (schemaCode == null || schemaCode.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT id, schema_code, schema_name, scene_id, schema_content, prompt_fragment, sample_output,
                       description, is_active, create_time, update_time
                FROM llm_output_schema
                WHERE schema_code = ?
                """, this::mapRow, schemaCode.trim()).stream().findFirst();
    }

    @Transactional
    public LlmOutputSchema save(LlmOutputSchema schema) {
        LlmOutputSchema normalized = normalize(schema);
        if (normalized.id() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO llm_output_schema
                            (schema_code, schema_name, scene_id, schema_content, prompt_fragment, sample_output,
                             description, is_active)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                bind(statement, normalized);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            return findById(key == null ? 0 : key.longValue()).orElse(normalized);
        }

        int updated = jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    UPDATE llm_output_schema
                    SET schema_code = ?,
                        schema_name = ?,
                        scene_id = ?,
                        schema_content = ?,
                        prompt_fragment = ?,
                        sample_output = ?,
                        description = ?,
                        is_active = ?
                    WHERE id = ?
                    """);
            bind(statement, normalized);
            statement.setLong(9, normalized.id());
            return statement;
        });
        if (updated == 0) {
            throw new IllegalArgumentException("输出结构不存在: " + normalized.id());
        }
        return findById(normalized.id()).orElse(normalized);
    }

    @Transactional
    public void setActive(long id, boolean active) {
        int updated = jdbcTemplate.update("UPDATE llm_output_schema SET is_active = ? WHERE id = ?",
                active ? 1 : 0, id);
        if (updated == 0) {
            throw new IllegalArgumentException("输出结构不存在: " + id);
        }
    }

    @Transactional
    public void delete(long id) {
        int deleted = jdbcTemplate.update("DELETE FROM llm_output_schema WHERE id = ?", id);
        if (deleted == 0) {
            throw new IllegalArgumentException("输出结构不存在: " + id);
        }
    }

    public long countPromptReferences(long schemaId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_prompt WHERE output_schema_id = ?",
                Long.class,
                schemaId);
        return total == null ? 0 : total;
    }

    private LlmOutputSchema mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new LlmOutputSchema(
                rs.getLong("id"),
                rs.getString("schema_code"),
                rs.getString("schema_name"),
                nullableLong(rs, "scene_id"),
                rs.getString("schema_content"),
                rs.getString("prompt_fragment"),
                rs.getString("sample_output"),
                rs.getString("description"),
                rs.getInt("is_active") == 1,
                toLocalDateTime(rs.getTimestamp("create_time")),
                toLocalDateTime(rs.getTimestamp("update_time")));
    }

    private LlmOutputSchema normalize(LlmOutputSchema schema) {
        if (schema == null) {
            throw new IllegalArgumentException("输出结构不能为空");
        }
        String schemaCode = trim(schema.schemaCode());
        String schemaName = trim(schema.schemaName());
        if (schemaCode.isEmpty()) {
            throw new IllegalArgumentException("输出结构编码不能为空");
        }
        if (schemaName.isEmpty()) {
            throw new IllegalArgumentException("输出结构名称不能为空");
        }
        String schemaContent = trim(schema.schemaContent());
        String promptFragment = trim(schema.promptFragment());
        if (schemaContent.isEmpty() && promptFragment.isEmpty()) {
            throw new IllegalArgumentException("输出结构内容不能为空");
        }
        return new LlmOutputSchema(
                schema.id(),
                schemaCode,
                schemaName,
                schema.sceneId(),
                schemaContent,
                promptFragment,
                blankToEmpty(schema.sampleOutput()),
                blankToEmpty(schema.description()),
                schema.active(),
                schema.createTime(),
                schema.updateTime());
    }

    private void bind(java.sql.PreparedStatement statement, LlmOutputSchema schema) throws SQLException {
        statement.setString(1, schema.schemaCode());
        statement.setString(2, schema.schemaName());
        setNullableLong(statement, 3, schema.sceneId());
        statement.setString(4, schema.schemaContent());
        statement.setString(5, schema.promptFragment());
        statement.setString(6, schema.sampleOutput());
        statement.setString(7, schema.description());
        statement.setInt(8, schema.active() ? 1 : 0);
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

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record SearchTerm(String raw, String like) {
    }
}
