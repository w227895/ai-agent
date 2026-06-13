# Few-Shot Prompt 管理平台（一期）

本项目是一个基于 Spring Boot + Spring AI + DeepSeek 的 Few-Shot Prompt 管理平台，用于把航变邮件、航变短信、售后邮件等场景中的 Prompt、Few-shot 示例和模型返回格式从代码中抽离出来，改为可配置、可测试、可沉淀的管理能力。

## 一、项目背景

当前航变邮件、售后邮件、航变短信等业务场景大量依赖 Prompt Engineering，存在以下问题：

- Prompt 写死在代码里，调整需要发版。
- Few-shot 样例写死在代码里，失败样本无法快速补充。
- 模型返回格式写死在代码里，字段规范难以统一。
- 不同业务场景缺少统一管理入口。
- 测试结果无法持续沉淀，Prompt 调优链路不闭环。

一期目标：

- 支持 Prompt 配置化。
- 支持 Few-shot 配置化。
- 支持返回格式模板配置化。
- 支持在线组装 Prompt 并测试模型效果。
- 保存测试记录，为后续 Prompt 调优和 Few-shot 补充提供依据。

## 二、系统架构

```text
场景
 ↓
主提示词
 ↓
 ├── Few-shot
 │
 ├── 返回格式模板
 │
 └── 在线测试
          ↓
      测试结果
```

核心执行链路：

```text
选择场景
 ↓
选择主提示词
 ↓
加载启用的 Few-shot
 ↓
加载返回格式模板
 ↓
输入测试内容
 ↓
组装最终 Prompt
 ↓
调用模型
 ↓
返回结果
 ↓
保存测试记录
```

最终 Prompt 组装顺序：

```text
系统 Prompt
+
返回格式模板
+
Few-shot 1
+
Few-shot 2
+
Few-shot N
+
用户输入
```

## 三、功能模块

### 3.1 场景管理

用于管理业务场景，例如：

- 航变邮件
- 航变短信
- 售后邮件
- 行李邮件
- 退款邮件

页面能力：

- 新增场景
- 编辑场景
- 删除场景
- 启用、禁用
- 按编码、名称、描述查询

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 主键 |
| sceneCode | String | 场景编码 |
| sceneName | String | 场景名称 |
| description | String | 场景描述 |
| status | Integer | 状态，1 启用，0 禁用 |
| createTime | DateTime | 创建时间 |

当前实现对应：

- 表：`llm_scenario`
- 代码对象：`FewShotScenario`
- 当前字段名以 `scenario_code`、`scenario_name`、`is_active` 为主。

### 3.2 主提示词管理

管理场景对应的 Prompt。

关系：

```text
一个场景
 ↓
多个主提示词
```

页面能力：

- 新增主提示词
- 编辑主提示词
- 删除主提示词
- 复制主提示词
- 启用、禁用
- 设置默认主提示词
- 按优先级选择生效 Prompt

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 主键 |
| sceneId | Long | 场景 ID |
| promptName | String | 提示词名称 |
| systemPrompt | Text | 系统提示词 |
| modelName | String | 模型名称 |
| priority | Integer | 优先级 |
| version | String | 版本号 |
| status | Integer | 状态，1 启用，0 禁用 |

当前实现对应：

- 表：`llm_prompt`
- 代码对象：`LlmPromptTemplate`
- 当前以 `prompt_code` 作为提示词唯一编码，暂未单独保存 `promptName`、`modelName`、`version`。

### 3.3 返回格式模板管理

统一管理模型输出结构，避免 JSON 格式不统一、字段缺失、字段名称不一致。

页面能力：

- 新增返回格式模板
- 编辑返回格式模板
- 删除返回格式模板
- JSON 格式校验
- 模板复制
- 启用、禁用

示例：

```json
{
  "flightNo": "",
  "changeType": "",
  "oldDepartureTime": "",
  "newDepartureTime": "",
  "oldArrivalTime": "",
  "newArrivalTime": "",
  "departureAirport": "",
  "arrivalAirport": "",
  "pnr": "",
  "passengerNames": []
}
```

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 主键 |
| promptId | Long | 主提示词 ID |
| templateName | String | 模板名称 |
| templateContent | Text | JSON 模板 |
| requiredFields | Text | 必填字段 |
| status | Integer | 状态，1 启用，0 禁用 |

当前实现对应：

- 表：`llm_output_schema`
- 代码对象：`LlmOutputSchema`
- 当前字段名以 `schema_code`、`schema_name`、`schema_json`、`field_description_json`、`empty_value_rule` 为主。
- `requiredFields` 暂未独立建字段，可先放在 `field_description_json` 或后续扩表。

### 3.4 Few-shot 管理

管理主提示词下的输入/输出示例样本。

关系：

```text
一个主提示词
 ↓
多个 Few-shot
```

页面能力：

- 新增 Few-shot
- 编辑 Few-shot
- 删除 Few-shot
- 排序
- 复制
- 启用、禁用

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 主键 |
| promptId | Long | 主提示词 ID |
| userExample | Text | 输入样例 |
| assistantExample | Text | 输出样例 |
| description | String | 说明 |
| sort | Integer | 排序 |
| status | Integer | 状态，1 启用，0 禁用 |

当前实现对应：

- 表：`llm_prompt_few_shot`
- 代码对象：`FewShotExample`
- 当前字段名以 `input_text`、`expected_output`、`title`、`tags_json`、`sort_order`、`enabled` 为主。

### 3.5 在线测试

用于验证当前场景、主提示词、Few-shot 和返回格式模板的组合效果。

页面布局：

- 左侧：场景选择。
- 中间：Prompt 配置、Few-shot 列表、返回格式模板。
- 右侧：测试输入、Prompt 预览、响应结果。

执行流程：

```text
系统 Prompt
+
返回格式模板
+
Few-shot 列表
+
用户输入
 ↓
组装最终 Prompt
 ↓
调用模型
 ↓
返回结果
```

展示内容：

- Prompt 预览：展示最终组装后的 System Prompt 和 User Prompt。
- 响应结果：展示模型返回内容。
- Token 统计：输入 Token、输出 Token、总 Token。
- 耗时统计：调用耗时、模型耗时、总耗时。

当前实现对应：

- 接口：`POST /few-shot/prompt-preview`
- 接口：`POST /few-shot/run`
- 页面：`src/main/resources/static/index.html` 的“运行测试”模块。
- 当前已支持 Prompt 预览、运行测试、响应展示、Token 估算和耗时统计。

### 3.6 测试结果管理

保存所有测试记录，用于 Prompt 调优、Few-shot 补充和效果分析。

页面能力：

- 查询测试记录
- 查看测试详情
- 删除测试记录
- 导出测试结果
- 从失败记录沉淀 Few-shot

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 主键 |
| sceneId | Long | 场景 ID |
| promptId | Long | 提示词 ID |
| templateId | Long | 模板 ID |
| requestContent | Text | 测试输入 |
| finalPrompt | LongText | 最终 Prompt |
| responseContent | LongText | 返回结果 |
| tokenUsage | Integer | Token 消耗 |
| costTime | Integer | 耗时，单位 ms |
| status | String | SUCCESS/FAIL |
| createTime | DateTime | 创建时间 |

当前实现对应：

- 表：`few_shot_run_log`
- 当前已保存 `scenario_code`、`prompt_code`、`schema_code`、`input_text`、`final_prompt`、`output_text`、`status`、Token 估算和耗时。
- 当前页面已支持测试结果列表、详情查看、删除和 CSV 导出。

## 四、一期范围

一期必须完成：

- 场景管理基础 CRUD。
- 主提示词管理基础 CRUD。
- 返回格式模板管理基础 CRUD 和 JSON 校验。
- Few-shot 管理基础 CRUD、排序、启用禁用。
- 在线测试，支持 Prompt 预览、模型调用、响应展示。
- 测试结果保存，至少记录输入、输出、状态、耗时和创建时间。

一期可延后：

- Prompt 多版本对比。
- 自动评测准确率。
- 失败样本自动推荐 Few-shot。
- 多模型成本统计。
- 审批发布流程。
- 权限体系和操作审计。

## 五、当前项目运行

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

启动：

```powershell
mvn spring-boot:run
```

当前机器也可以使用临时 Maven：

```powershell
& "C:\tmp\apache-maven-3.9.9\bin\mvn.cmd" spring-boot:run
```

页面地址：

```text
http://localhost:8080/
```

## 六、当前接口

列出场景：

```powershell
Invoke-RestMethod "http://localhost:8080/few-shot/scenarios"
```

分页查询场景：

```powershell
Invoke-RestMethod "http://localhost:8080/few-shot/scenarios/page?page=1&size=10&keyword=航变"
```

查看提示词预览：

```powershell
Invoke-RestMethod "http://localhost:8080/few-shot/scenarios/flight-change-mail/prompt-preview"
```

运行测试：

```powershell
Invoke-RestMethod "http://localhost:8080/few-shot/run" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"scenarioCode":"flight-change-mail","input":"Subject: 航班变更通知 MU5101\n原航班 MU5101 变更为 MU5115，票号 781-1234567890，PNR H8K2Q9。"}'
```

兼容邮件识别入口：

```powershell
Invoke-RestMethod "http://localhost:8080/mail/process"
```

## 七、代码边界

- `fewshot/FewShotScenario`：场景定义，负责关联提示词和输出结构。
- `fewshot/LlmPromptTemplate`：主提示词定义，并持有 Few-shot 示例和输出结构模板。
- `fewshot/LlmOutputSchema`：模型返回格式模板。
- `fewshot/FewShotExample`：Few-shot 示例。
- `fewshot/FewShotScenarioRepository`：基于 MySQL/JdbcTemplate 持久化场景、提示词、样例、输出结构和运行日志。
- `fewshot/FewShotPlatformService`：统一组装 Prompt 并调用模型。
- `controller/FewShotController`：通用 Few-shot 平台接口。
- `controller/MailController`：航变邮件识别兼容入口。

## 八、后续落地建议

建议按以下顺序继续补齐：

1. 将当前 `prompt_code`、`schema_code` 日志字段进一步升级为稳定的 `prompt_id`、`template_id` 快照。
2. 增加 Few-shot 独立启用/禁用接口，避免只能通过保存主提示词整体更新。
3. 增加测试结果一键沉淀为 Few-shot。
4. 增加 Prompt 多版本对比和发布审批。
5. 增加权限体系和操作审计。
