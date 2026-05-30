# 航变邮件处理中心

一个 Spring Boot + Spring AI + DeepSeek 的航变邮件解析 demo。

处理链路：

```text
航变邮件来了
↓
识别
↓
提取
↓
关联订单
↓
生成工单
↓
通知人工
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

当前机器也已临时下载 Maven 到 `C:\tmp\apache-maven-3.9.9`，可以这样运行：

```powershell
& "C:\tmp\apache-maven-3.9.9\bin\mvn.cmd" spring-boot:run
```

## 页面

```text
http://localhost:8080/
```

## 接口

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

## AI 结合方式

`AiConfig` 把 `FlightChangeWorkflowTools` 注册给 DeepSeek：

```java
.defaultTools(workflowTools)
```

模型负责识别邮件、提取字段、决定工具调用顺序和参数。Spring AI 负责把模型的 tool call 映射到 Java 方法。

当前工具：

- `findRelatedOrder`：关联订单
- `createFlightChangeWorkOrder`：生成航变工单
- `notifyHumanAgent`：通知人工坐席

当前订单、工单、通知服务是本地实现，已经按服务边界拆开。后续接真实系统时替换 `service` 包里的实现即可。
