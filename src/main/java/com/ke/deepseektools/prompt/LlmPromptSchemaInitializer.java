package com.ke.deepseektools.prompt;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LlmPromptSchemaInitializer {

    private static final String[] LEGACY_SCENE_CODES = {
            "supplier_type_detect",
            "email_type_detect",
            "email_flight_change_extract",
            "email_refund_extract",
            "email_change_extract",
            "sms_type_detect",
            "sms_flight_change_extract"
    };

    private final JdbcTemplate jdbcTemplate;

    public LlmPromptSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    @Transactional
    public void initialize() {
        ensureSceneTable();
        ensurePromptSceneIdColumn();
        ensureOutputSchemaTable();
        ensureOutputSchemaColumns();
        ensureLegacyOutputSchemaColumns();
        ensureOutputSchemaIndexes();
        ensurePromptOutputSchemaIdColumn();
        ensurePromptFewShotTable();
        ensurePromptFewShotColumns();
        ensurePromptFewShotIndexes();
        migratePromptFewShotColumn();
        ensurePromptSceneIndex();
        ensurePromptOutputSchemaIndex();
        dropLegacySceneTypeUniqueIndex();
        ensureSceneCodeUniqueIndex();
        seedScenes();
        seedOutputSchemas();
        backfillPromptScenes();
        backfillPromptOutputSchemas();
        removeLegacyScenes();
    }

    private void ensureSceneTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `llm_prompt_scene` (
                  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `scene_code` varchar(100) NOT NULL DEFAULT '' COMMENT '场景编码',
                  `scene_name` varchar(100) NOT NULL DEFAULT '' COMMENT '场景名称',
                  `description` varchar(500) NOT NULL DEFAULT '' COMMENT '说明',
                  `is_active` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否启用(1:启用,0:禁用)',
                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (`id`) USING BTREE,
                  UNIQUE KEY `uk_llm_prompt_scene_code` (`scene_code`) USING BTREE,
                  KEY `idx_llm_prompt_scene_active` (`is_active`) USING BTREE
                ) ENGINE = InnoDB DEFAULT CHARSET = utf8 ROW_FORMAT = DYNAMIC COMMENT = '大模型业务场景表'
                """);
    }

    private void seedScenes() {
        for (LlmPromptScenario scene : PromptDictionaries.scenarios()) {
            jdbcTemplate.update("""
                    INSERT INTO `llm_prompt_scene`
                    (`scene_code`, `scene_name`, `description`, `is_active`)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      `scene_name` = VALUES(`scene_name`),
                      `description` = VALUES(`description`),
                      `is_active` = VALUES(`is_active`)
                    """,
                    scene.sceneCode(),
                    scene.sceneName(),
                    scene.description(),
                    scene.active() ? 1 : 0);
        }
    }

    private void ensurePromptSceneIdColumn() {
        if (!columnExists("llm_prompt", "scene_id")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt` ADD COLUMN `scene_id` bigint(20) unsigned NULL COMMENT '关联场景ID' AFTER `id`");
        }
    }

    private void ensureOutputSchemaTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `llm_output_schema` (
                  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `schema_code` varchar(100) NOT NULL DEFAULT '' COMMENT '输出结构编码',
                  `schema_name` varchar(100) NOT NULL DEFAULT '' COMMENT '输出结构名称',
                  `scene_id` bigint(20) unsigned NULL COMMENT '关联场景ID',
                  `schema_content` longtext NULL COMMENT '结构定义',
                  `prompt_fragment` longtext NULL COMMENT '拼接到提示词的输出结构说明',
                  `sample_output` longtext NULL COMMENT '示例输出',
                  `description` varchar(500) NOT NULL DEFAULT '' COMMENT '说明',
                  `is_active` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否启用(1:启用,0:禁用)',
                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (`id`) USING BTREE,
                  UNIQUE KEY `uk_llm_output_schema_code` (`schema_code`) USING BTREE,
                  KEY `idx_llm_output_schema_scene_id` (`scene_id`) USING BTREE,
                  KEY `idx_llm_output_schema_active` (`is_active`) USING BTREE
                ) ENGINE = InnoDB DEFAULT CHARSET = utf8 ROW_FORMAT = DYNAMIC COMMENT = '大模型输出结构表'
                """);
    }

    private void ensureOutputSchemaColumns() {
        if (!columnExists("llm_output_schema", "schema_code")) {
            jdbcTemplate.execute("ALTER TABLE `llm_output_schema` ADD COLUMN `schema_code` varchar(100) NOT NULL DEFAULT '' COMMENT '输出结构编码' AFTER `id`");
        }
        if (!columnExists("llm_output_schema", "schema_name")) {
            jdbcTemplate.execute("ALTER TABLE `llm_output_schema` ADD COLUMN `schema_name` varchar(100) NOT NULL DEFAULT '' COMMENT '输出结构名称' AFTER `schema_code`");
        }
        if (!columnExists("llm_output_schema", "scene_id")) {
            jdbcTemplate.execute("ALTER TABLE `llm_output_schema` ADD COLUMN `scene_id` bigint(20) unsigned NULL COMMENT '关联场景ID' AFTER `schema_name`");
        }
        if (!columnExists("llm_output_schema", "schema_content")) {
            jdbcTemplate.execute("ALTER TABLE `llm_output_schema` ADD COLUMN `schema_content` longtext NULL COMMENT '结构定义' AFTER `scene_id`");
        }
        if (!columnExists("llm_output_schema", "prompt_fragment")) {
            jdbcTemplate.execute("ALTER TABLE `llm_output_schema` ADD COLUMN `prompt_fragment` longtext NULL COMMENT '拼接到提示词的输出结构说明' AFTER `schema_content`");
        }
        if (!columnExists("llm_output_schema", "sample_output")) {
            jdbcTemplate.execute("ALTER TABLE `llm_output_schema` ADD COLUMN `sample_output` longtext NULL COMMENT '示例输出' AFTER `prompt_fragment`");
        }
        if (!columnExists("llm_output_schema", "description")) {
            jdbcTemplate.execute("ALTER TABLE `llm_output_schema` ADD COLUMN `description` varchar(500) NOT NULL DEFAULT '' COMMENT '说明' AFTER `sample_output`");
        }
        if (!columnExists("llm_output_schema", "is_active")) {
            jdbcTemplate.execute("ALTER TABLE `llm_output_schema` ADD COLUMN `is_active` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否启用(1:启用,0:禁用)' AFTER `description`");
        }
        if (!columnExists("llm_output_schema", "create_time")) {
            jdbcTemplate.execute("ALTER TABLE `llm_output_schema` ADD COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER `is_active`");
        }
        if (!columnExists("llm_output_schema", "update_time")) {
            jdbcTemplate.execute("ALTER TABLE `llm_output_schema` ADD COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`");
        }
    }

    private void ensureLegacyOutputSchemaColumns() {
        if (columnExists("llm_output_schema", "schema_json")) {
            jdbcTemplate.execute("ALTER TABLE `llm_output_schema` MODIFY COLUMN `schema_json` longtext NULL COMMENT '历史结构定义字段'");
        }
    }

    private void ensurePromptOutputSchemaIdColumn() {
        if (!columnExists("llm_prompt", "output_schema_id")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt` ADD COLUMN `output_schema_id` bigint(20) unsigned NULL COMMENT '关联输出结构ID' AFTER `scene_id`");
        }
    }

    private void ensurePromptFewShotTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `llm_prompt_few_shot` (
                  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                  `prompt_id` bigint(20) unsigned NOT NULL COMMENT '关联提示词ID',
                  `title` varchar(200) NOT NULL DEFAULT '' COMMENT 'Few-shot 标题',
                  `content` text NOT NULL COMMENT 'Few-shot 示例内容',
                  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '排序值(数字越小越靠前)',
                  `is_active` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否启用(1:启用,0:禁用)',
                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  PRIMARY KEY (`id`) USING BTREE,
                  KEY `idx_llm_prompt_few_shot_prompt_id` (`prompt_id`) USING BTREE,
                  KEY `idx_llm_prompt_few_shot_active` (`is_active`) USING BTREE
                ) ENGINE = InnoDB DEFAULT CHARSET = utf8 ROW_FORMAT = DYNAMIC COMMENT = '提示词 Few-shot 示例表'
                """);
    }

    private void ensurePromptFewShotColumns() {
        if (!columnExists("llm_prompt_few_shot", "prompt_id")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt_few_shot` ADD COLUMN `prompt_id` bigint(20) unsigned NULL COMMENT '关联提示词ID' AFTER `id`");
        }
        if (!columnExists("llm_prompt_few_shot", "title")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt_few_shot` ADD COLUMN `title` varchar(200) NOT NULL DEFAULT '' COMMENT 'Few-shot 标题' AFTER `prompt_id`");
        }
        if (!columnExists("llm_prompt_few_shot", "content")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt_few_shot` ADD COLUMN `content` text NULL COMMENT 'Few-shot 示例内容' AFTER `title`");
        }
        if (!columnExists("llm_prompt_few_shot", "sort_order")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt_few_shot` ADD COLUMN `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '排序值(数字越小越靠前)' AFTER `content`");
        }
        if (!columnExists("llm_prompt_few_shot", "is_active")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt_few_shot` ADD COLUMN `is_active` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否启用(1:启用,0:禁用)' AFTER `sort_order`");
        }
        if (!columnExists("llm_prompt_few_shot", "create_time")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt_few_shot` ADD COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER `is_active`");
        }
        if (!columnExists("llm_prompt_few_shot", "update_time")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt_few_shot` ADD COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`");
        }
    }

    private void ensurePromptFewShotIndexes() {
        if (!indexExists("llm_prompt_few_shot", "idx_llm_prompt_few_shot_prompt_id")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt_few_shot` ADD KEY `idx_llm_prompt_few_shot_prompt_id` (`prompt_id`) USING BTREE");
        }
        if (!indexExists("llm_prompt_few_shot", "idx_llm_prompt_few_shot_active")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt_few_shot` ADD KEY `idx_llm_prompt_few_shot_active` (`is_active`) USING BTREE");
        }
    }

    private void ensureOutputSchemaIndexes() {
        if (!indexExists("llm_output_schema", "uk_llm_output_schema_code")) {
            jdbcTemplate.execute("ALTER TABLE `llm_output_schema` ADD UNIQUE KEY `uk_llm_output_schema_code` (`schema_code`) USING BTREE");
        }
        if (!indexExists("llm_output_schema", "idx_llm_output_schema_scene_id")) {
            jdbcTemplate.execute("ALTER TABLE `llm_output_schema` ADD KEY `idx_llm_output_schema_scene_id` (`scene_id`) USING BTREE");
        }
        if (!indexExists("llm_output_schema", "idx_llm_output_schema_active")) {
            jdbcTemplate.execute("ALTER TABLE `llm_output_schema` ADD KEY `idx_llm_output_schema_active` (`is_active`) USING BTREE");
        }
    }

    private void migratePromptFewShotColumn() {
        if (!columnExists("llm_prompt", "few_shot")) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO llm_prompt_few_shot
                    (prompt_id, title, content, sort_order, is_active)
                SELECT p.id, '默认 Few-shot', p.few_shot, 0, 1
                FROM llm_prompt p
                LEFT JOIN llm_prompt_few_shot f ON f.prompt_id = p.id
                WHERE p.few_shot IS NOT NULL
                  AND TRIM(p.few_shot) <> ''
                  AND f.prompt_id IS NULL
                """);
    }

    private void ensurePromptSceneIndex() {
        if (!indexExists("llm_prompt", "idx_llm_prompt_scene_id")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt` ADD KEY `idx_llm_prompt_scene_id` (`scene_id`) USING BTREE");
        }
    }

    private void ensurePromptOutputSchemaIndex() {
        if (!indexExists("llm_prompt", "idx_llm_prompt_output_schema_id")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt` ADD KEY `idx_llm_prompt_output_schema_id` (`output_schema_id`) USING BTREE");
        }
    }

    private void dropLegacySceneTypeUniqueIndex() {
        if (indexExists("llm_prompt_scene", "uk_llm_prompt_scene_type")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt_scene` DROP INDEX `uk_llm_prompt_scene_type`");
        }
    }

    private void ensureSceneCodeUniqueIndex() {
        if (!indexExists("llm_prompt_scene", "uk_llm_prompt_scene_code")) {
            jdbcTemplate.execute("ALTER TABLE `llm_prompt_scene` ADD UNIQUE KEY `uk_llm_prompt_scene_code` (`scene_code`) USING BTREE");
        }
    }

    private void backfillPromptScenes() {
        setSceneByRule("flight_email", """
                (p.code_type IN ('1', '2') AND p.template_type IN ('1', '2'))
                """);
        setSceneByRule("flight_sms", """
                (p.code_type IN ('3', '4'))
                """);
        setSceneByRule("after_sale_email", """
                (p.code_type = 'after-sale'
                 OR (p.code_type = '2' AND p.template_type NOT IN ('1', '2'))
                OR (p.code_type = '2' AND p.mail_type IN (2, 3, 9, 10)))
                """);
    }

    private void seedOutputSchemas() {
        for (OutputSchemaSeed seed : outputSchemaSeeds()) {
            jdbcTemplate.update("""
                    INSERT INTO `llm_output_schema`
                    (`schema_code`, `schema_name`, `scene_id`, `schema_content`, `prompt_fragment`,
                     `sample_output`, `description`, `is_active`)
                    VALUES (
                      ?, ?,
                      (SELECT id FROM llm_prompt_scene WHERE scene_code = ? LIMIT 1),
                      ?, ?, ?, ?, 1
                    )
                    ON DUPLICATE KEY UPDATE
                      `schema_name` = VALUES(`schema_name`),
                      `scene_id` = VALUES(`scene_id`),
                      `schema_content` = VALUES(`schema_content`),
                      `prompt_fragment` = VALUES(`prompt_fragment`),
                      `sample_output` = VALUES(`sample_output`),
                      `description` = VALUES(`description`),
                      `is_active` = VALUES(`is_active`)
                    """,
                    seed.schemaCode(),
                    seed.schemaName(),
                    seed.sceneCode(),
                    seed.schemaContent(),
                    seed.promptFragment(),
                    seed.sampleOutput(),
                    seed.description());
        }
    }

    private void backfillPromptOutputSchemas() {
        setOutputSchemaByRule("flight_change_extract", """
                ((p.code_type IN ('1', '2') AND p.template_type = '1')
                 OR (p.code_type = '4' AND p.template_type = '1'))
                """);
        setOutputSchemaByRule("flight_cancel_extract", """
                (p.code_type = '4' AND p.template_type = '2')
                """);
        setOutputSchemaByRule("sms_type_detect", """
                (p.code_type = '3' AND p.template_type = '4')
                """);
        setOutputSchemaByRule("after_sale_type_detect", """
                (p.code_type = '2' AND p.template_type = '3')
                """);
        setOutputSchemaByRule("after_sale_reissue_extract", """
                (p.code_type = '2' AND p.template_type = '5')
                """);
        setOutputSchemaByRule("after_sale_name_gender", """
                (p.code_type = '2' AND p.template_type = '6' AND p.mail_type IN (9, 10))
                """);
    }

    private void setOutputSchemaByRule(String schemaCode, String condition) {
        jdbcTemplate.update("""
                UPDATE llm_prompt p
                JOIN llm_output_schema os ON os.schema_code = ?
                SET p.output_schema_id = os.id
                WHERE p.output_schema_id IS NULL
                  AND %s
                """.formatted(condition), schemaCode);
    }

    private void setSceneByRule(String sceneCode, String condition) {
        jdbcTemplate.update("""
                UPDATE llm_prompt p
                JOIN llm_prompt_scene s ON s.scene_code = ?
                SET p.scene_id = s.id
                WHERE %s
                """.formatted(condition), sceneCode);
    }

    private OutputSchemaSeed[] outputSchemaSeeds() {
        return new OutputSchemaSeed[] {
                new OutputSchemaSeed(
                        "flight_change_extract",
                        "航变字段提取结构",
                        "flight_email",
                        """
                                {
                                  "type": "object",
                                  "required": ["airLinePnrList", "ticketNumList", "pnrCode", "orderId", "supplierOrderNum", "passengerNameList", "originFlightChangeDetailList", "newFlightChangeDetailList"],
                                  "properties": {
                                    "airLinePnrList": {"type": "array", "items": {"type": "string"}},
                                    "ticketNumList": {"type": "array", "items": {"type": "string"}},
                                    "pnrCode": {"type": "string"},
                                    "orderId": {"type": ["integer", "null"]},
                                    "supplierOrderNum": {"type": "string"},
                                    "passengerNameList": {"type": "array", "items": {"type": "string"}},
                                    "originFlightChangeDetailList": {"type": "array", "items": {"$ref": "#/$defs/flightDetail"}},
                                    "newFlightChangeDetailList": {"type": "array", "items": {"$ref": "#/$defs/flightDetail"}}
                                  },
                                  "$defs": {
                                    "flightDetail": {
                                      "type": "object",
                                      "required": ["flightNum", "depTime", "arrTime", "depAirport", "arrAirport", "cabin"],
                                      "properties": {
                                        "flightNum": {"type": "string"},
                                        "depTime": {"type": "string"},
                                        "arrTime": {"type": "string"},
                                        "depAirport": {"type": "string"},
                                        "arrAirport": {"type": "string"},
                                        "cabin": {"type": "string"}
                                      }
                                    }
                                  }
                                }
                                """,
                        """
                                请严格输出合法纯 JSON，不要 Markdown 和额外解释。
                                输出结构固定为：
                                {
                                  "airLinePnrList": [],
                                  "ticketNumList": [],
                                  "pnrCode": "",
                                  "orderId": null,
                                  "supplierOrderNum": "",
                                  "passengerNameList": [],
                                  "originFlightChangeDetailList": [
                                    {
                                      "flightNum": "",
                                      "depTime": "",
                                      "arrTime": "",
                                      "depAirport": "",
                                      "arrAirport": "",
                                      "cabin": ""
                                    }
                                  ],
                                  "newFlightChangeDetailList": [
                                    {
                                      "flightNum": "",
                                      "depTime": "",
                                      "arrTime": "",
                                      "depAirport": "",
                                      "arrAirport": "",
                                      "cabin": ""
                                    }
                                  ]
                                }
                                未提及的数组返回 []，未提及的字符串返回 ""，未明确订单号时 orderId 返回 null。
                                """,
                        """
                                {
                                  "airLinePnrList": [],
                                  "ticketNumList": [],
                                  "pnrCode": "",
                                  "orderId": null,
                                  "supplierOrderNum": "",
                                  "passengerNameList": [],
                                  "originFlightChangeDetailList": [],
                                  "newFlightChangeDetailList": []
                                }
                                """,
                        "航变邮件、航变短信的通用字段提取输出结构"),
                new OutputSchemaSeed(
                        "flight_cancel_extract",
                        "航班取消字段提取结构",
                        "flight_sms",
                        """
                                {
                                  "type": "object",
                                  "required": ["airLinePnrList", "ticketNumList", "pnrCode", "orderId", "supplierOrderNum", "passengerNameList", "originFlightChangeDetailList", "newFlightChangeDetailList"],
                                  "properties": {
                                    "airLinePnrList": {"type": "array", "items": {"type": "string"}},
                                    "ticketNumList": {"type": "array", "items": {"type": "string"}},
                                    "pnrCode": {"type": "string"},
                                    "orderId": {"type": ["integer", "null"]},
                                    "supplierOrderNum": {"type": "string"},
                                    "passengerNameList": {"type": "array", "items": {"type": "string"}},
                                    "originFlightChangeDetailList": {"type": "array"},
                                    "newFlightChangeDetailList": {"type": "array"}
                                  }
                                }
                                """,
                        """
                                请严格输出合法纯 JSON，不要 Markdown 和额外解释。
                                航班取消通知使用固定结构：
                                {
                                  "airLinePnrList": [],
                                  "ticketNumList": [],
                                  "pnrCode": "",
                                  "orderId": null,
                                  "supplierOrderNum": "",
                                  "passengerNameList": [],
                                  "originFlightChangeDetailList": [],
                                  "newFlightChangeDetailList": []
                                }
                                取消的原航班放入 originFlightChangeDetailList。没有明确保护航班、替代航班或新航班时，newFlightChangeDetailList 必须返回 []。
                                """,
                        """
                                {
                                  "airLinePnrList": [],
                                  "ticketNumList": [],
                                  "pnrCode": "",
                                  "orderId": null,
                                  "supplierOrderNum": "",
                                  "passengerNameList": [],
                                  "originFlightChangeDetailList": [],
                                  "newFlightChangeDetailList": []
                                }
                                """,
                        "航班取消短信字段提取输出结构"),
                new OutputSchemaSeed(
                        "sms_type_detect",
                        "短信类型识别结构",
                        "flight_sms",
                        """
                                {
                                  "type": "object",
                                  "required": ["success", "entityType", "entityCode", "smsType"],
                                  "properties": {
                                    "success": {"type": "boolean"},
                                    "entityType": {"type": ["integer", "null"]},
                                    "entityCode": {"type": ["string", "null"]},
                                    "smsType": {"type": ["integer", "null"]}
                                  }
                                }
                                """,
                        """
                                请只输出如下 JSON 结构：
                                {
                                  "success": true,
                                  "entityType": null,
                                  "entityCode": null,
                                  "smsType": null
                                }
                                不要输出解释、Markdown 或额外字段。
                                """,
                        """
                                {
                                  "success": true,
                                  "entityType": 1,
                                  "entityCode": "WZH",
                                  "smsType": 1
                                }
                                """,
                        "短信业务类型识别输出结构"),
                new OutputSchemaSeed(
                        "after_sale_type_detect",
                        "售后类型识别结构",
                        "after_sale_email",
                        """
                                {
                                  "type": "object",
                                  "required": ["isSuccess", "errorMsg", "type", "reason", "segmentList", "passengerList", "purchaseOrderId"],
                                  "properties": {
                                    "isSuccess": {"type": "boolean"},
                                    "errorMsg": {"type": ["string", "null"]},
                                    "type": {"type": ["integer", "null"]},
                                    "reason": {"type": ["string", "null"]},
                                    "segmentList": {"type": "array"},
                                    "passengerList": {"type": "array"},
                                    "purchaseOrderId": {"type": ["string", "null"]}
                                  }
                                }
                                """,
                        """
                                请只输出售后类型识别 JSON：
                                {
                                  "isSuccess": true,
                                  "errorMsg": null,
                                  "type": null,
                                  "reason": "",
                                  "segmentList": [],
                                  "passengerList": [],
                                  "purchaseOrderId": null
                                }
                                """,
                        """
                                {
                                  "isSuccess": true,
                                  "errorMsg": null,
                                  "type": 4,
                                  "reason": "",
                                  "segmentList": [],
                                  "passengerList": [],
                                  "purchaseOrderId": null
                                }
                                """,
                        "Agoda 售后邮件类型识别输出结构"),
                new OutputSchemaSeed(
                        "after_sale_reissue_extract",
                        "售后改期字段结构",
                        "after_sale_email",
                        """
                                {
                                  "type": "object",
                                  "required": ["isSuccess", "errorMsg", "type", "segmentList", "passengerList", "purchaseOrderId", "reissueReason", "refundReasonType"],
                                  "properties": {
                                    "isSuccess": {"type": "boolean"},
                                    "errorMsg": {"type": ["string", "null"]},
                                    "type": {"type": ["integer", "null"]},
                                    "segmentList": {"type": "array"},
                                    "passengerList": {"type": "array"},
                                    "purchaseOrderId": {"type": ["string", "null"]},
                                    "reissueReason": {"type": ["string", "null"]},
                                    "refundReasonType": {"type": ["string", "null"]}
                                  }
                                }
                                """,
                        """
                                请只输出售后改期字段 JSON：
                                {
                                  "isSuccess": true,
                                  "errorMsg": null,
                                  "type": null,
                                  "segmentList": [],
                                  "passengerList": [],
                                  "purchaseOrderId": null,
                                  "reissueReason": null,
                                  "refundReasonType": null
                                }
                                """,
                        """
                                {
                                  "isSuccess": true,
                                  "errorMsg": null,
                                  "type": 6,
                                  "segmentList": [],
                                  "passengerList": [],
                                  "purchaseOrderId": null,
                                  "reissueReason": null,
                                  "refundReasonType": null
                                }
                                """,
                        "售后改期类字段提取输出结构"),
                new OutputSchemaSeed(
                        "after_sale_name_gender",
                        "改名/性别字段结构",
                        "after_sale_email",
                        """
                                {
                                  "type": "object",
                                  "required": ["isSuccess", "errorMsg", "type", "segmentList", "passengerList", "purchaseOrderId", "reissueReason", "refundReasonType"],
                                  "properties": {
                                    "isSuccess": {"type": "boolean"},
                                    "errorMsg": {"type": ["string", "null"]},
                                    "type": {"type": "integer"},
                                    "segmentList": {"type": "array"},
                                    "passengerList": {"type": "array"},
                                    "purchaseOrderId": {"type": ["string", "null"]},
                                    "reissueReason": {"type": ["string", "null"]},
                                    "refundReasonType": {"type": ["string", "null"]}
                                  }
                                }
                                """,
                        """
                                请只输出改名/性别处理 JSON：
                                {
                                  "isSuccess": true,
                                  "errorMsg": null,
                                  "type": 9,
                                  "segmentList": [],
                                  "passengerList": [
                                    {
                                      "originalName": null,
                                      "name": null,
                                      "originalGender": null,
                                      "gender": null
                                    }
                                  ],
                                  "purchaseOrderId": null,
                                  "reissueReason": null,
                                  "refundReasonType": null
                                }
                                type 按当前提示词规则输出 9 或 10。
                                """,
                        """
                                {
                                  "isSuccess": true,
                                  "errorMsg": null,
                                  "type": 9,
                                  "segmentList": [],
                                  "passengerList": [],
                                  "purchaseOrderId": null,
                                  "reissueReason": null,
                                  "refundReasonType": null
                                }
                                """,
                        "售后改名/性别申请与确认输出结构")
        };
    }

    private void removeLegacyScenes() {
        for (String sceneCode : LEGACY_SCENE_CODES) {
            jdbcTemplate.update("""
                    DELETE FROM llm_prompt_scene
                    WHERE scene_code = ?
                      AND id NOT IN (
                        SELECT scene_id FROM llm_prompt WHERE scene_id IS NOT NULL
                      )
                    """, sceneCode);
        }
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

    private record OutputSchemaSeed(
            String schemaCode,
            String schemaName,
            String sceneCode,
            String schemaContent,
            String promptFragment,
            String sampleOutput,
            String description) {
    }
}
