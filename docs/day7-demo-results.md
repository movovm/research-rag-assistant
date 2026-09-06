# Day 7 第 2 项实际演示结果

## 基线与环境

- 提交：`6644f9f feat: add persistent asynchronous sales review tasks`
- 模式：`local`
- 专用 MySQL 容器：`salesmentor-day7-demo-mysql`（隔离数据库 `salesmentor_demo`，宿主端口 `3307`）
- 应用端口：`8081`
- local 模式未使用 Redis、Pinecone 或云 API 凭据。
- Flyway 在应用启动时执行 V1/V2/V3；数据库只包含本次 synthetic 演示数据。

## 启动记录

按指南执行 `mvn spring-boot:run` 时，Maven 插件在本机以 `ClassNotFoundException: com.salesmentor.SalesMentorApplication` 退出。源码随后通过 `mvn -DskipTests package` 成功打包，并使用同一构建产物 `java -jar target/salesmentor-1.0.0.jar` 在 `8081` 启动，根路径返回 HTTP 200。

这说明当前环境中 `java -jar` 可复现；Maven 运行插件的类路径问题属于本机启动方式限制，未修改生产代码或配置。

## Day 7 第 4 项当前源码复核

为排除旧 JAR 影响，使用当前工作树再次执行 `mvn clean verify`：`152 tests`，0 failures、0 errors、0 skipped，真实 MySQL Testcontainers 与 Flyway V1/V2/V3 通过。随后使用该次构建生成的 `target/salesmentor-1.0.0.jar`，连接同一专用数据库、local 模式、端口 `8081` 启动；根路径返回 HTTP `200`，启动日志显示 `Start-Class: com.salesmentor.SalesMentorApplication`、Flyway schema version `3` 和 `Tomcat started on port 8081`。

因此，JAR 启动已由当前源码重新构建并验证。`mvn spring-boot:run` 的已确认事实仍是插件运行阶段找不到主类；现有 POM 未提供足以定位单一根因的自定义配置，本项不修改 POM 或生产代码。此前四类 HTTP 复盘结果仍来自前一次同一分支构建的 local 隔离演示，本轮没有重复调用四类复盘接口。

## 四类复盘任务

以下是实际 HTTP 输出摘要，不是固定示例值。四个任务均返回 `ACCEPTED`，最终状态均为 `SUCCEEDED`，每个任务的 Trace 步骤均为 `1,2,3`。SSE 首次读取已直接观察到终态快照，未要求必须先看到 `RUNNING`。

| 请求文件 | taskId | 当前观察数 | 历史经验数 | 产品事实数 | 引用 ID | limitations |
|---|---:|---:|---:|---:|---|---|
| `review-experience-only.json` | `2096569929673904129` | 1 | 0 | 0 | `CUR-1` | `未找到已发布且已索引的匹配证据`; `evidence insufficient` |
| `review-product-only.json` | `2096569931397763073` | 1 | 0 | 3 | `CUR-1`, `DOC-3002`, `DOC-3001`, `DOC-3003` | 空 |
| `review-both-signals.json` | `2096569932442144769` | 1 | 0 | 3 | `CUR-1`, `DOC-3002`, `DOC-3001`, `DOC-3003` | `未找到已发布且已索引的匹配证据` |
| `review-no-retrieval.json` | `2096569933616549889` | 1 | 0 | 0 | `CUR-1` | `no retrieval tool selected` |

产品引用来自 V2 synthetic 文档：`DOC-3001` 产品概览、`DOC-3002` 价格说明、`DOC-3003` 部署说明。由于隔离库没有已发布且已索引的经验，包含经验信号的任务如实返回空历史经验，没有伪造 `EXP-*`。

幂等验证使用 `day7-experience-only-001`：相同 requestId 和完全相同输入复用 taskId `2096569929673904129`，返回 `NOT_PENDING/SUCCEEDED`，没有重复任务。相同 requestId 但修改 conversationContent 返回 HTTP `409`。

## 经验资产链路

通过 `POST /api/v1/cases` 导入 synthetic 案例，实际返回：

- `caseId=2096570555044630529`
- 案例状态：`EXTRACTED`
- 经验数量：1
- 经验 ID：`2096570555258540034`
- `reviewStatus=GENERATED`
- `indexStatus=NOT_INDEXED`
- `evidenceStart=0`、`evidenceEnd=27`
- `evidenceQuote` 精确对应案例原文的前 27 个字符

本项没有自动调用 verify 或 publish，保留人工判断边界。因此“发布后经验检索”尚未完成；需要人工确认该经验内容和证据，再调用审核与发布接口，随后等待 `PUBLISHED + INDEXED` 后才能验证经验检索。

## 未执行与限制

- 未运行 cloud 模式；没有配置或使用真实 DashScope、Pinecone、Redis。
- 未执行人工 verify/publish，未声称经验链路已完整跑通。
- 没有把任务级 Trace 解读为具体 Tool 调用明细。
- 没有验证成交效果、建议质量或生产容量。

## 资源收尾

演示应用进程和 `salesmentor-day7-demo-mysql` 容器已在本项结束时关闭；容器卷保留，未停止其他服务，未删除任何既有资源。
