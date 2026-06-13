CREATE TABLE IF NOT EXISTS llm_prompt (
    id BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    scenario_code VARCHAR(128) NOT NULL DEFAULT '' COMMENT '所属场景编码',
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
    KEY idx_llm_prompt_scenario_code (scenario_code) USING BTREE,
    KEY idx_supplier_code (prompt_code) USING BTREE,
    KEY idx_is_active (is_active) USING BTREE,
    KEY idx_priority (priority) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大模型配置表';

CREATE TABLE IF NOT EXISTS llm_scenario (
    id BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    scenario_code VARCHAR(128) NOT NULL DEFAULT '' COMMENT '场景编码',
    scenario_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '场景名称',
    description TEXT NULL COMMENT '场景说明',
    input_label VARCHAR(128) NOT NULL DEFAULT '邮件正文' COMMENT '输入标签',
    prompt_id BIGINT(20) UNSIGNED NOT NULL COMMENT '关联llm_prompt主键ID',
    schema_code VARCHAR(128) NOT NULL DEFAULT '' COMMENT '输出结构编码',
    system_instruction TEXT NULL COMMENT '兼容旧业务指令',
    output_contract TEXT NULL COMMENT '兼容旧输出契约',
    tool_profile VARCHAR(128) NOT NULL DEFAULT 'none' COMMENT '工具配置',
    is_active TINYINT(4) NOT NULL DEFAULT 1 COMMENT '是否启用(1:启用,0:禁用)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id) USING BTREE,
    UNIQUE KEY uk_llm_scenario_code (scenario_code) USING BTREE,
    KEY idx_llm_scenario_prompt_id (prompt_id) USING BTREE,
    KEY idx_llm_scenario_active (is_active) USING BTREE,
    CONSTRAINT fk_llm_scenario_prompt
        FOREIGN KEY (prompt_id) REFERENCES llm_prompt (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大模型场景表';

CREATE TABLE IF NOT EXISTS llm_output_schema (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    prompt_id BIGINT(20) UNSIGNED NULL COMMENT '所属主提示词ID',
    schema_code VARCHAR(128) NOT NULL DEFAULT '' COMMENT '输出结构编码',
    schema_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '输出结构名称',
    schema_json MEDIUMTEXT NOT NULL COMMENT '目标JSON结构',
    field_description_json MEDIUMTEXT NULL COMMENT '字段业务说明',
    empty_value_rule TEXT NULL COMMENT '空值规则',
    is_active TINYINT(4) NOT NULL DEFAULT 1 COMMENT '是否启用(1:启用,0:禁用)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_llm_output_schema_code (schema_code),
    KEY idx_llm_output_schema_prompt_id (prompt_id),
    KEY idx_llm_output_schema_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大模型输出结构配置表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词Few-shot示例表';

CREATE TABLE IF NOT EXISTS few_shot_run_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario_code VARCHAR(128) NOT NULL,
    scenario_name VARCHAR(255) NOT NULL,
    tool_profile VARCHAR(128) NOT NULL,
    prompt_code VARCHAR(500) NOT NULL DEFAULT '',
    schema_code VARCHAR(128) NOT NULL DEFAULT '',
    input_text MEDIUMTEXT NOT NULL,
    final_prompt LONGTEXT NULL,
    output_text MEDIUMTEXT NULL,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    error_message TEXT NULL,
    elapsed_ms BIGINT NOT NULL DEFAULT 0,
    model_time_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_few_shot_run_log_scenario_created (scenario_code, created_at),
    KEY idx_few_shot_run_log_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS few_shot_failure_case (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario_code VARCHAR(128) NOT NULL,
    input_text MEDIUMTEXT NOT NULL,
    actual_output MEDIUMTEXT NULL,
    expected_output MEDIUMTEXT NOT NULL,
    problem_note TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_few_shot_failure_case_scenario_created (scenario_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
