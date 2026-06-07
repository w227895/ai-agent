CREATE TABLE IF NOT EXISTS few_shot_scenario (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(128) NOT NULL,
    prompt_code VARCHAR(500) NOT NULL DEFAULT '',
    schema_code VARCHAR(128) NOT NULL DEFAULT '',
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    input_label VARCHAR(128) NOT NULL,
    system_instruction TEXT NULL,
    output_contract TEXT NULL,
    tool_profile VARCHAR(128) NOT NULL DEFAULT 'none',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_few_shot_scenario_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS llm_prompt (
    id BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    prompt_code VARCHAR(500) NOT NULL DEFAULT '' COMMENT '代码',
    code_type VARCHAR(50) NOT NULL DEFAULT '' COMMENT '代码类型，1为供应商，2为邮箱',
    template_type VARCHAR(50) NOT NULL DEFAULT '' COMMENT '模板类型',
    user_prompt VARCHAR(2048) NOT NULL DEFAULT '' COMMENT '用户提示词',
    priority INT(11) NOT NULL DEFAULT 0 COMMENT '匹配优先级(数字越小优先级越高)',
    is_active TINYINT(4) NOT NULL DEFAULT 1 COMMENT '是否启用(1:启用,0:禁用)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    system_prompt VARCHAR(8000) NOT NULL DEFAULT '' COMMENT '系统提示词',
    mail_type INT(11) NOT NULL DEFAULT 0 COMMENT '邮件识别类型，第二段字段提取使用。第一段类型识别为0',
    PRIMARY KEY (id) USING BTREE,
    KEY idx_supplier_code (prompt_code) USING BTREE,
    KEY idx_is_active (is_active) USING BTREE,
    KEY idx_priority (priority) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大模型配置表';

CREATE TABLE IF NOT EXISTS llm_output_schema (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    schema_code VARCHAR(128) NOT NULL DEFAULT '' COMMENT '输出结构编码',
    schema_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '输出结构名称',
    schema_json MEDIUMTEXT NOT NULL COMMENT '目标JSON结构',
    field_description_json MEDIUMTEXT NULL COMMENT '字段业务说明',
    empty_value_rule TEXT NULL COMMENT '空值规则',
    is_active TINYINT(4) NOT NULL DEFAULT 1 COMMENT '是否启用(1:启用,0:禁用)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_llm_output_schema_code (schema_code),
    KEY idx_llm_output_schema_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大模型输出结构配置表';

CREATE TABLE IF NOT EXISTS few_shot_example (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario_id BIGINT NOT NULL,
    example_key VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    input_text MEDIUMTEXT NOT NULL,
    expected_output MEDIUMTEXT NOT NULL,
    tags_json TEXT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_few_shot_example_key (scenario_id, example_key),
    KEY idx_few_shot_example_scenario (scenario_id, sort_order),
    CONSTRAINT fk_few_shot_example_scenario
        FOREIGN KEY (scenario_id) REFERENCES few_shot_scenario (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS few_shot_run_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario_code VARCHAR(128) NOT NULL,
    scenario_name VARCHAR(255) NOT NULL,
    tool_profile VARCHAR(128) NOT NULL,
    input_text MEDIUMTEXT NOT NULL,
    output_text MEDIUMTEXT NULL,
    status VARCHAR(32) NOT NULL,
    error_message TEXT NULL,
    elapsed_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_few_shot_run_log_scenario_created (scenario_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
