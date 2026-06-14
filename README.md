# 大模型提示词管理

这是一个收敛后的 Spring Boot + Spring AI + DeepSeek 工具，只保留三类功能：

- 场景管理：维护 `code_type / template_type / mail_type` 的业务含义。
- 提示词管理：维护 `llm_prompt` 表，并通过 `scene_id` 关联场景。
- 提示词测试：选择提示词和邮件内容后调用大模型返回结果。

## 页面

启动后打开：

```text
http://localhost:8080/
```

左侧入口：

- 场景管理：新增、查看、编辑、启用禁用、删除场景。
- 提示词管理：新增、查看、编辑、启用禁用、删除提示词。
- 提示词测试：选择提示词、填写发件人和邮件正文，执行模型调用。

## 接口

```text
GET    /api/scenes?page=1&size=20&keyword=
GET    /api/scenes/active
GET    /api/scenes/{id}
POST   /api/scenes
PUT    /api/scenes/{id}
POST   /api/scenes/{id}/status?active=true
DELETE /api/scenes/{id}

GET    /api/prompts?page=1&size=20&keyword=
GET    /api/prompts/{id}
POST   /api/prompts
PUT    /api/prompts/{id}
POST   /api/prompts/{id}/status?active=true
DELETE /api/prompts/{id}

GET    /api/dictionaries
POST   /api/test
```

保存提示词时必须传 `sceneId`。后端会读取场景，并自动同步写入 `code_type`、`template_type`、`mail_type`。

`POST /api/test` 示例：

```json
{
  "promptId": 3,
  "sender": "",
  "emailContent": "邮件正文"
}
```

不传 `promptId` 时，可以传 `sender`，系统会用启用状态且 `code_type = 2` 的 `prompt_code` 做邮箱匹配。

## 数据库

项目按生产表结构初始化 `llm_prompt`，并新增 `scene_id` 字段：

```sql
`scene_id` bigint(20) unsigned NULL COMMENT '关联场景ID'
```

新增 `llm_prompt_scene` 场景表，用来解释 `code_type + template_type + mail_type` 的业务含义，并通过唯一键避免重复场景：

```sql
UNIQUE KEY `uk_llm_prompt_scene_type` (`code_type`, `template_type`, `mail_type`) USING BTREE
```

`schema.sql` 会预置默认场景数据：

- 供应商类型识别：`1 / 1 / 0`
- 邮箱类型识别：`2 / 1 / 0`
- 邮箱航变字段提取：`2 / 2 / 1`
- 邮箱退票字段提取：`2 / 2 / 2`
- 邮箱改期字段提取：`2 / 2 / 3`
- 短信类型识别：`4 / 1 / 0`
- 短信航变字段提取：`4 / 2 / 1`

启动时会自动做兼容处理：

- 旧库没有 `llm_prompt.scene_id` 时自动补列。
- 旧库没有 `idx_llm_prompt_scene_id` 时自动补索引。
- 旧库会按 `code_type + template_type + mail_type` 回填 `scene_id`。
- 场景预制数据使用唯一键幂等写入，重复启动不会重复插入。

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
