CREATE TABLE IF NOT EXISTS few_shot_scenario (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(128) NOT NULL,
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
