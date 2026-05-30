# Spring AI DeepSeek Function Calling Demo

一个最小 Spring Boot + Spring AI + DeepSeek 的 function calling 示例。

天气工具会调用 Open-Meteo 的真实接口：

- Geocoding API：城市名转经纬度
- Forecast API：按经纬度查询天气预报

订单金额计算保留为本地业务函数，方便演示模型一次请求里连续调用多个工具。

## 准备

设置 DeepSeek API Key：

```powershell
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
```

## 运行

```powershell
mvn spring-boot:run
```

如果本机没有安装 Maven，可以安装 Maven 后运行上面的命令，或在 IDE 中直接导入 `pom.xml`。
当前机器也已临时下载 Maven 到 `C:\tmp\apache-maven-3.9.9`，可以这样运行：

```powershell
& "C:\tmp\apache-maven-3.9.9\bin\mvn.cmd" spring-boot:run
```

## 调用

```powershell
Invoke-RestMethod "http://localhost:8080/ai/function-call"
```

也可以传入自己的问题：

```powershell
Invoke-RestMethod "http://localhost:8080/ai/function-call?message=我明天去上海，天气怎么样？另外 5 件 88 元商品打 9 折是多少钱？"
```

控制台如果看到类似日志，说明模型触发了本地工具调用：

```text
Tool called: getCityWeather(city=上海)
Tool called: calculateOrderPrice(unitPrice=88, quantity=5, discountRate=0.9)
```

天气接口来源：

- https://geocoding-api.open-meteo.com/v1/search
- https://api.open-meteo.com/v1/forecast
