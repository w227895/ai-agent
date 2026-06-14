package com.ke.deepseektools.prompt;

import java.util.List;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LlmPromptSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public LlmPromptSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    @Transactional
    public void initialize() {
        ensureSceneTable();
        seedScenes();
        ensurePromptSceneIdColumn();
        ensurePromptSceneIndex();
        backfillPromptScenes();
    }

    private void ensureSceneTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `llm_prompt_scene` (
                  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `scene_code` varchar(100) NOT NULL DEFAULT '' COMMENT '场景编码',
                  `scene_name` varchar(100) NOT NULL DEFAULT '' COMMENT '场景名称',
                  `code_type` varchar(50) NOT NULL DEFAULT '' COMMENT '代码类型',
                  `code_type_name` varchar(100) NOT NULL DEFAULT '' COMMENT '代码类型名称',
                  `template_type` varchar(50) NOT NULL DEFAULT '' COMMENT '模板类型',
                  `template_type_name` varchar(100) NOT NULL DEFAULT '' COMMENT '模板类型名称',
                  `mail_type` int(11) NOT NULL DEFAULT '0' COMMENT '邮件识别类型',
                  `mail_type_name` varchar(100) NOT NULL DEFAULT '' COMMENT '邮件识别类型名称',
                  `description` varchar(500) NOT NULL DEFAULT '' COMMENT '说明',
                  `is_active` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否启用(1:启用,0:禁用)',
                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (`id`) USING BTREE,
                  UNIQUE KEY `uk_llm_prompt_scene_type` (`code_type`, `template_type`, `mail_type`) USING BTREE,
                  KEY `idx_llm_prompt_scene_code` (`scene_code`) USING BTREE,
                  KEY `idx_llm_prompt_scene_active` (`is_active`) USING BTREE
                ) ENGINE = InnoDB DEFAULT CHARSET = utf8 ROW_FORMAT = DYNAMIC COMMENT = '大模型场景标识表'
                """);
    }

    private void seedScenes() {
        for (LlmPromptScenario scene : PromptDictionaries.scenarios()) {
            jdbcTemplate.update("""
                    INSERT INTO `llm_prompt_scene`
                    (`scene_code`, `scene_name`, `code_type`, `code_type_name`, `template_type`, `template_type_name`,
                     `mail_type`, `mail_type_name`, `description`, `is_active`)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      `scene_code` = VALUES(`scene_code`),
                      `scene_name` = VALUES(`scene_name`),
                      `code_type_name` = VALUES(`code_type_name`),
                      `template_type_name` = VALUES(`template_type_name`),
                      `mail_type_name` = VALUES(`mail_type_name`),
                      `description` = VALUES(`description`),
                      `is_active` = VALUES(`is_active`)
                    """,
                    scene.sceneCode(),
                    scene.sceneName(),
                    scene.codeType(),
                    scene.codeTypeName(),
                    scene.templateType(),
                    scene.templateTypeName(),
                    scene.mailType(),
                    scene.mailTypeName(),
                    scene.description(),
                    scene.active() ? 1 : 0);
        }
    }

    private void ensurePromptSceneIdColumn() {
        if (!columnExists("llm_prompt", "scene_id")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt` ADD COLUMN `scene_id` bigint(20) unsigned NULL COMMENT '关联场景ID' AFTER `id`");
        }
    }

    private void ensurePromptSceneIndex() {
        if (!indexExists("llm_prompt", "idx_llm_prompt_scene_id")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt` ADD KEY `idx_llm_prompt_scene_id` (`scene_id`) USING BTREE");
        }
    }

    private void backfillPromptScenes() {
        jdbcTemplate.update("""
                UPDATE llm_prompt p
                JOIN llm_prompt_scene s
                  ON s.code_type = p.code_type
                 AND s.template_type = p.template_type
                 AND s.mail_type = p.mail_type
                SET p.scene_id = s.id
                WHERE p.scene_id IS NULL
                """);
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, tableName, indexName);
        return count != null && count > 0;
    }
}
