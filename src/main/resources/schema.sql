CREATE TABLE IF NOT EXISTS `llm_prompt` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scene_id` bigint(20) unsigned NULL COMMENT '关联场景ID',
  `output_schema_id` bigint(20) unsigned NULL COMMENT '关联输出结构ID',
  `prompt_code` varchar(500) NOT NULL DEFAULT '' COMMENT '代码',
  `code_type` varchar(50) NOT NULL DEFAULT '' COMMENT '代码类型，1为供应商，2为邮箱，3/4为短信',
  `template_type` varchar(50) NOT NULL DEFAULT '' COMMENT '模板类型',
  `user_prompt` varchar(2048) NOT NULL DEFAULT '' COMMENT '用户提示词',
  `priority` int(11) NOT NULL DEFAULT '0' COMMENT '匹配优先级(数字越小优先级越高)',
  `is_active` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否启用(1:启用,0:禁用)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `system_prompt` varchar(8000) NOT NULL DEFAULT '' COMMENT '系统提示词',
  `mail_type` int(11) NOT NULL DEFAULT '0' COMMENT '邮件识别类型，第二段字段提取使用。第一段类型识别为0',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_llm_prompt_scene_id` (`scene_id`) USING BTREE,
  KEY `idx_llm_prompt_output_schema_id` (`output_schema_id`) USING BTREE,
  KEY `idx_supplier_code` (`prompt_code`) USING BTREE,
  KEY `idx_is_active` (`is_active`) USING BTREE,
  KEY `idx_priority` (`priority`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8 ROW_FORMAT = DYNAMIC COMMENT = '大模型配置表';

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
) ENGINE = InnoDB DEFAULT CHARSET = utf8 ROW_FORMAT = DYNAMIC COMMENT = '大模型业务场景表';

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
) ENGINE = InnoDB DEFAULT CHARSET = utf8 ROW_FORMAT = DYNAMIC COMMENT = '大模型输出结构表';

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
) ENGINE = InnoDB DEFAULT CHARSET = utf8 ROW_FORMAT = DYNAMIC COMMENT = '提示词 Few-shot 示例表';

INSERT INTO `llm_prompt_scene`
(`scene_code`, `scene_name`, `description`, `is_active`)
VALUES
('flight_email', '航变邮件识别', '邮件和供应商维度的航变识别、航变字段提取提示词', 1),
('flight_sms', '航变短信识别', '短信渠道的航变识别、航变字段提取提示词', 1),
('after_sale_email', '售后邮件识别', '退票、改期、售后补充等邮件类售后提示词', 1)
ON DUPLICATE KEY UPDATE
  `scene_name` = VALUES(`scene_name`),
  `description` = VALUES(`description`),
  `is_active` = VALUES(`is_active`);
