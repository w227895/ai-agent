# Few-shot 邮件识别平台

一个 Spring Boot + Spring AI + DeepSeek 的提示词场景平台。

当前只保留两类能力：

- 管理场景与提示词的关联关系。
- 在提示词下维护并组装 few-shot 示例。
- 运行航变邮件识别场景，提取邮件里的关键字段。

## 核心链路

```text
输入邮件/文本
→ 选择场景并定位关联提示词
→ 拼装提示词 System Prompt + 输出契约 + 提示词的 few-shot 示例
→ 拼装 User Prompt
→ 调用 DeepSeek
→ 返回识别结果并记录运行日志
```

## 准备

设置 DeepSeek API Key：

```powershell
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
```

本地默认使用 MySQL：

```text
url: jdbc:mysql://localhost:3306/ai_agent
username: root
password: root123456
```

可以用环境变量覆盖：

```powershell
$env:MYSQL_URL="jdbc:mysql://localhost:3306/ai_agent?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="root123456"
```

应用启动时会执行 `schema.sql`，自动创建 `llm_scenario`、`llm_prompt`、
`llm_prompt_few_shot`、`llm_output_schema` 和运行日志表。

核心关系：

```text
llm_scenario.prompt_id → llm_prompt.id
llm_prompt_few_shot.prompt_id → llm_prompt.id
```

已有库中的 `few_shot_scenario`、`few_shot_example` 会在启动时迁移到新表，旧表不会自动删除。

## 运行

```powershell
mvn spring-boot:run
```

当前机器也可以使用临时 Maven：

```powershell
& "C:\tmp\apache-maven-3.9.9\bin\mvn.cmd" spring-boot:run
```

## 页面

```text
http://localhost:8080/
```

页面可以编辑并保存场景配置，也可以直接查看最终发给模型的 `System Prompt` 和 `User Prompt`。

## 接口

列出场景：

```powershell
Invoke-RestMethod "http://localhost:8080/few-shot/scenarios"
```

运行场景：

```powershell
Invoke-RestMethod "http://localhost:8080/few-shot/run" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"scenarioCode":"flight-change-mail","input":"Subject: 航班变更通知 MU5101\n原航班 MU5101 变更为 MU5115，票号 781-1234567890，PNR H8K2Q9。"}'
```

查看已保存场景的提示词预览：

```powershell
Invoke-RestMethod "http://localhost:8080/few-shot/scenarios/flight-change-mail/prompt-preview"
```

兼容邮件识别入口：

```powershell
Invoke-RestMethod "http://localhost:8080/mail/process"
```

也可以 POST 邮件正文：

```powershell
Invoke-RestMethod "http://localhost:8080/mail/process" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"emailContent":"Subject: 航班变更通知 MU5101\n旅客张三，票号 781-1234567890，PNR H8K2Q9，原航班 MU5101 变更为 MU5115。"}'
```

## 代码边界

- `fewshot/FewShotScenario`：场景定义，负责关联提示词和输出结构。
- `fewshot/LlmPromptTemplate`：提示词定义，并持有用于组装的 few-shot 示例。
- `fewshot/FewShotScenarioRepository`：基于 MySQL/JdbcTemplate 持久化场景、提示词样例和运行日志。
- `fewshot/FewShotPlatformService`：统一拼装提示词并调用模型。
- `controller/FewShotController`：通用 few-shot 平台接口。
- `controller/MailController`：航变邮件识别兼容入口。
