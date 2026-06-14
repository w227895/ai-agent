CREATE TABLE IF NOT EXISTS `llm_prompt` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scene_id` bigint(20) unsigned NULL COMMENT '关联场景ID',
  `prompt_code` varchar(500) NOT NULL DEFAULT '' COMMENT '代码',
  `code_type` varchar(50) NOT NULL DEFAULT '' COMMENT '代码类型，1为供应商，2为邮箱',
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
  KEY `idx_supplier_code` (`prompt_code`) USING BTREE,
  KEY `idx_is_active` (`is_active`) USING BTREE,
  KEY `idx_priority` (`priority`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8 ROW_FORMAT = DYNAMIC COMMENT = '大模型配置表';

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
) ENGINE = InnoDB DEFAULT CHARSET = utf8 ROW_FORMAT = DYNAMIC COMMENT = '大模型场景标识表';

INSERT INTO `llm_prompt_scene`
(`scene_code`, `scene_name`, `code_type`, `code_type_name`, `template_type`, `template_type_name`, `mail_type`, `mail_type_name`, `description`, `is_active`)
VALUES
('supplier_type_detect', '供应商类型识别', '1', '供应商', '1', '航变解析模板', 0, '类型识别', '供应商维度识别', 1),
('email_type_detect', '邮箱类型识别', '2', '邮箱', '1', '航变解析模板', 0, '类型识别', '邮件第一段：识别邮件类型', 1),
('email_flight_change_extract', '邮箱航变字段提取', '2', '邮箱', '2', '字段提取模板', 1, '航变字段提取', '邮件第二段：提取航变字段', 1),
('email_refund_extract', '邮箱退票字段提取', '2', '邮箱', '2', '字段提取模板', 2, '退票字段提取', '邮件第二段：提取退票字段', 1),
('email_change_extract', '邮箱改期字段提取', '2', '邮箱', '2', '字段提取模板', 3, '改期字段提取', '邮件第二段：提取改期字段', 1),
('sms_type_detect', '短信类型识别', '4', '短信', '1', '航变解析模板', 0, '类型识别', '短信第一段：识别短信类型', 1),
('sms_flight_change_extract', '短信航变字段提取', '4', '短信', '2', '字段提取模板', 1, '航变字段提取', '短信第二段：提取航变字段', 1)
ON DUPLICATE KEY UPDATE
  `scene_code` = VALUES(`scene_code`),
  `scene_name` = VALUES(`scene_name`),
  `code_type_name` = VALUES(`code_type_name`),
  `template_type_name` = VALUES(`template_type_name`),
  `mail_type_name` = VALUES(`mail_type_name`),
  `description` = VALUES(`description`),
  `is_active` = VALUES(`is_active`);
