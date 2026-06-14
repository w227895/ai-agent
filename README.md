# 大模型提示词管理

这是一个收敛后的 Spring Boot + Spring AI + DeepSeek 小工具，只保留两个功能：

- `llm_prompt` 数据表增删改查
- 选择提示词和邮件内容后调用大模型返回结果

## 页面

启动后打开：

```text
http://localhost:8080/
```

左侧只有两个入口：

- 大模型配置：维护 `llm_prompt` 表
- 提示词测试：选择提示词、填写发件人和邮件内容，执行模型调用

## 接口

```text
GET    /api/prompts?page=1&size=20&keyword=
GET    /api/prompts/{id}
POST   /api/prompts
PUT    /api/prompts/{id}
POST   /api/prompts/{id}/status?active=true
DELETE /api/prompts/{id}
POST   /api/test
```

`POST /api/test` 示例：

```json
{
  "promptId": 3,
  "sender": "",
  "emailContent": "邮件正文"
}
```

不传 `promptId` 时，可以传 `sender`，系统会用启用状态且 `code_type = 2` 的 `prompt_code` 做邮箱匹配。

## 数据表

项目按生产表结构初始化 `llm_prompt`：

```sql
CREATE TABLE `llm_prompt` (
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
```

## 配置

默认配置在 `src/main/resources/application.yml`：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${MYSQL_URL:jdbc:mysql://localhost:3306/ai_agent?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:root123456}
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:not-configured}
```

启动前请配置真实的 MySQL 和 `DEEPSEEK_API_KEY`。
