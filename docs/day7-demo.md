# SalesMentor V1 Day 7 演示指南

本文只描述可复现的本地流程。下面的响应均为“按 DTO 编写的结构示例”，不是本次未启动服务后的实际运行输出；`taskId`、时间和耗时必须以本机返回为准。

## 1. 启动前置

- JDK 17+、Maven 3.6+、Docker Desktop。
- PowerShell 5+ 或 PowerShell 7。
- local 模式仍需要 MySQL：`docker compose up -d mysql`，默认端口 `3306`，Flyway 启动时自动执行 V1/V2/V3。
- local 模式使用进程内 Feature Hashing embedding、向量索引、BM25 和内存会话，不需要 Redis、Pinecone 或 API Key。
- `server.port` 默认来自 `SERVER_PORT`，未设置时为 `8080`；数据库连接来自 `MYSQL_URL`、`MYSQL_USER`、`MYSQL_PASSWORD`，默认值见 `src/main/resources/application.yml`。
- 首次启动会加载既有 RAG 示例资料；V2 中的 SalesMentor 产品资料是受信任的 synthetic 文档。启动成功不等于已完成业务演示，需以 HTTP 响应和任务状态为准。

```powershell
cd D:\Agent项目\salesmentor-repo
docker compose up -d mysql
$env:RAG_MODE = "local"
mvn clean verify
java -jar .\target\salesmentor-1.0.0.jar
```

在本机验证中，`mvn spring-boot:run` 曾在 Spring Boot Maven 插件启动阶段报 `ClassNotFoundException: com.salesmentor.SalesMentorApplication`；当前 POM 没有自定义运行类路径配置，原因尚未缩小到源码或插件配置中的单一缺陷。可复现的启动方式是先用当前源码完成 `mvn clean verify`，再运行刚生成的可执行 JAR；不要把旧 JAR 的结果当作当前源码验证。

停止本地依赖：

```powershell
docker compose stop mysql
```

cloud 模式需要额外的 DashScope、Pinecone 和 Redis 配置。不要把真实凭据写入仓库；使用环境变量或未跟踪的本地配置，并确认 Pinecone embedding 维度与配置一致。

## 2. 经验资产链路

### 2.1 导入案例

接口是 `POST /api/v1/cases`。`externalKey` 可选，`title`、`content` 必填，`sourceType` 默认 `USER_PROVIDED`；`industry`、`salesStage`、`customerRole` 可选。导入会提交本地/云抽取任务，响应中的 `caseId` 是动态生成的。

```powershell
$case = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/cases `
  -ContentType 'application/json; charset=utf-8' `
  -Body (Get-Content .\docs\examples\case-import.json -Raw -Encoding UTF8)
$case
```

使用 `$case.caseId` 查询案例和抽取出的经验：

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/cases/$($case.caseId)
Invoke-RestMethod http://localhost:8080/api/v1/cases/$($case.caseId)/experiences
```

等待案例状态为 `EXTRACTED` 后，逐条记录经验 ID，阅读 `evidenceQuote`、`evidenceStart`、`evidenceEnd`，再进行人工判断。`EXTRACTED` 只表示结构和证据校验通过，不表示经验优秀或应发布。

### 2.2 人工审核、发布和检索

对人工认可的经验调用：

```powershell
$experienceId = 123 # 替换为上一步真实返回的 ID
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/experiences/$experienceId/review:verify" `
  -ContentType 'application/json' -Body '{"reviewedBy":1001}'
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/experiences/$experienceId/publish"
```

只有 `PUBLISHED + INDEXED` 的经验才会被经验检索工具采用。发布包含向量和 BM25 索引写入，可能先返回 `202 Accepted`；再次读取经验确认状态后再检索：

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/experiences/search `
  -ContentType 'application/json; charset=utf-8' `
  -Body (Get-Content .\docs\examples\experience-search.json -Raw -Encoding UTF8)
```

不要用 SQL 直接修改审核或发布状态。抽取器可能是 local 确定性适配器，也可能是 cloud 占位适配器；cloud 抽取需要配置结构化 LLM，local 演示不需要 AI 凭据。

## 3. 复盘任务链路

任务接口为：

- `POST /api/reviews`：创建并尝试提交异步任务。
- `GET /api/reviews/{taskId}/events`：SSE 最新状态快照。
- `GET /api/reviews/{taskId}`：读取 MySQL 任务事实和成功报告。
- `GET /api/reviews/{taskId}/traces`：读取脱敏任务级 Trace。

请求字段必须是 `requestId`、`industry`、`salesStage`、`customerRole`、`conversationContent`、`reviewGoal`。`salesStage` 使用 `SalesCase.SalesStage` 枚举字符串；以下示例用 `NEGOTIATION`。

```powershell
$created = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/reviews `
  -ContentType 'application/json; charset=utf-8' `
  -Body (Get-Content .\docs\examples\review-experience-only.json -Raw -Encoding UTF8)
$created
$taskId = $created.taskId
```

### 3.1 四种规则信号

| 文件 | 规则命中 | 预期结果 |
|---|---|---|
| `review-experience-only.json` | `price objection`、`negotiation` | 仅 Experience Tool |
| `review-product-only.json` | `deployment`、`specification` | 仅 Product Tool |
| `review-both-signals.json` | 价格异议 + 产品部署/规格 | Experience → Product，最多两次调用 |
| `review-no-retrieval.json` | 不含规则信号 | 零 Tool 调用，含 `no retrieval tool selected` limitation |

产品示例词使用 V2 synthetic 资料中的 `deployment`、`specification`、`product`、`pricing` 等词项；产品来源门禁仍只接受受信任的 synthetic source。

### 3.2 观察状态和读取结果

```powershell
curl.exe -N -H "Accept: text/event-stream" "http://localhost:8080/api/reviews/$taskId/events"
Invoke-RestMethod "http://localhost:8080/api/reviews/$taskId"
Invoke-RestMethod "http://localhost:8080/api/reviews/$taskId/traces"
```

SSE 返回的 `ACCEPTED` 只表示任务进入有界执行器；任务可能在 POST 返回前已完成，因此不要假定初始 `taskStatus` 一定为 `PENDING`。SSE 是最新状态通知，可能从首次观察的 `PENDING` 直接跳到 `SUCCEEDED`，不提供完整历史回放。主动断开 SSE 不会取消后台任务，客户端可重新连接并读取最新快照。

`SUCCEEDED` 表示报告通过归属校验并成功保存 JSON 快照，不表示检索一定返回了证据。必须检查报告的 `limitations`、`references` 和各区域 evidence ID；`EMPTY`、`FAILED` 或无检索计划应如实保留限制说明。队列满时响应为 `503`、错误码 `REVIEW_QUEUE_FULL`，仍包含 `taskId`，任务保持 `PENDING`。

四个请求文件的 JSON 都是 UTF-8，可直接通过上述 PowerShell 命令读取。示例中的 requestId 仅用于演示：相同 requestId 且输入完全一致会幂等复用；相同 requestId 但任一输入不同返回 `409 REVIEW_INPUT_CONFLICT`。

## 4. 复现边界

本指南中的命令和响应不承诺固定输出；第 2 项的实际运行记录（包括动态 ID、状态和限制）见 [day7-demo-results.md](day7-demo-results.md)。测试中的 Testcontainers/Flyway 通过只证明自动化测试环境可用，不能替代本机 MySQL、索引和 cloud 凭据的准备。不要把动态 ID、状态、耗时或成交效果写成固定演示结果。
