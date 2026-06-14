CREATE TABLE IF NOT EXISTS `llm_prompt` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
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
  KEY `idx_supplier_code` (`prompt_code`) USING BTREE,
  KEY `idx_is_active` (`is_active`) USING BTREE,
  KEY `idx_priority` (`priority`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8 ROW_FORMAT = DYNAMIC COMMENT = '大模型配置表';
