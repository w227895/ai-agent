# Few-shot 场景平台

一个 Spring Boot + Spring AI + DeepSeek 的 few-shot 场景平台 demo。

当前内置两个场景：

- `flight-change-mail`：航变邮件处理，复用订单关联、工单生成、人工通知工具链。
- `customer-intent`：客户意图识别，不依赖业务工具，用于展示平台对非航变场景的兼容性。

## 核心链路

```text
输入内容
→ 选择场景
→ 拼装场景指令 + 输出契约 + few-shot 示例
→ 按 toolProfile 决定是否挂载业务工具
→ 调用模型
→ 返回结构化或摘要结果
```

## 准备

设置 DeepSeek API Key：

```powershell
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
```

## 运行

```powershell
mvn spring-boot:run
```

当前机器也可以使用临时下载的 Maven：

```powershell
& "C:\tmp\apache-maven-3.9.9\bin\mvn.cmd" spring-boot:run
```

## 页面

```text
http://localhost:8080/
```

## Few-shot 接口

列出场景：

```powershell
Invoke-RestMethod "http://localhost:8080/few-shot/scenarios"
```

运行场景：

```powershell
Invoke-RestMethod "http://localhost:8080/few-shot/run" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"scenarioCode":"customer-intent","input":"我这张票不想去了，想问能不能退。"}'
```

新增或更新场景：

```powershell
Invoke-RestMethod "http://localhost:8080/few-shot/scenarios" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{
    "code":"refund-policy",
    "name":"退票政策识别",
    "description":"识别客户是否需要退票政策说明。",
    "inputLabel":"客户原文",
    "systemInstruction":"判断用户是否在咨询退票规则，只基于输入回答。",
    "outputContract":"输出 JSON，字段包含 intent、reason。",
    "toolProfile":"none",
    "examples":[]
  }'
```

给场景新增示例：

```powershell
Invoke-RestMethod "http://localhost:8080/few-shot/scenarios/refund-policy/examples" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"id":"refund-001","title":"退票咨询","input":"这张票退票费怎么算？","expectedOutput":"{\"intent\":\"退票咨询\",\"reason\":\"用户询问退票费。\"}","tags":["退款"]}'
```

## 兼容接口

原航变接口仍保留，并委托到 `flight-change-mail` 场景：

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

- `fewshot/FewShotScenario`：场景定义，包含业务指令、输出契约、工具配置和示例。
- `fewshot/FewShotScenarioRepository`：当前为内存实现，后续可替换为数据库。
- `fewshot/FewShotPlatformService`：统一拼装 prompt，并按 `toolProfile` 挂载工具。
- `controller/FewShotController`：通用平台接口。
- `controller/MailController`：兼容旧航变入口。
- `tools/FlightChangeWorkflowTools`：航变业务工具链。

后续新增业务优先新增场景和示例；只有当场景需要查订单、建工单、发通知等真实动作时，再新增对应 tools 并扩展 `toolProfile` 的路由。
