项目描述：
面向交付与服务体系，对 SR问题单、RO网络操作单、RFC变更单、NRisk风险单 等工单做事中/事后质量合规检查的平台，用规则引擎 + AI 辅助自动核查工单填写规范性、操作合规性与变更风险定级准确性，替代人工逐单核查，支撑整改、定级定责与内控审计。

Netcare工单质检工具，设计6个主要作业功能“质检任务管理、事后质检发起、质检结果、规则管理、规则模板管理、企业/运营商独立质检模块”，实现质检经验IT化，灵活编排规则，检查模板自由组合，全量问题单质检自动化，快速支撑质量运营作业模式数字化。
主要规划的功能模块：发起质检、质检结果、规则管理、规则模板管理、群组维护、事中质检单复核； 本平台提供业务环节QA、业务归属Leader进行配置管理Group的群组维护，管理员自定义维护不同的质检规则， 管理员、工程师选择规则配置模板对事后SR单发起质检以及管理员维护事中规则模板Icare针对事中SR单发起质检， 并将所有质检任务汇总可视化管理。

技术栈：
Java 21、Spring Boot 3.5.15、MyBatis、Redis、PageHelper 、EasyExcel

工作职责：
面向质量中心-政企质检字段线下表管理页面，覆盖四张政企质检字段分类表 产品ABCD 划分清单、产品类别映射、核心部件、卓战核白 4 类配置数据，提供"分页查询 → 条件导出 → 模板下载 → Excel 批量导入"的完整数据管理闭环。独立负责 16 个 REST 接口的全链路开发（Controller → Service → DAO）

	1. Redis 固定窗口限流：按用户 + 操作类型维度计数，60 秒窗口，阈值从全局配置下发、代码钳制上限 10 次，窗口到期 key过期自动清零。导出、导入、模板下载各算独立的一类操作。
	2. 基于 EasyExcel 监听器实现流式批量导入：逐行解析、500 行/批批量落库，导入上限经配置中心动态下发并熔断，采用"首批清表 + 后续批次追加"策略，失败行单独收集并以 Excel 文件回传前端；
	3. 查询入参校验注解化下沉（数据字典校验 @DataDictValidation + 字段长度校验 @SizeValidation + 正则校验 @RegexpValidation），校验随 VO 内聚、接口层只负责编排；
	
Redis 分布式锁 + W3 待办独立线程池异步处理**：质检状态变化（派发/质检/复核/审核）会触发 W3 协作门户"发/删"待办，W3 是慢外部 HTTP 调用，不能同步做会拖死批量派发。
	1. 异步化改造：设计"异步管吞吐、锁管去重、DB 管最终一致"三件套——`@Async("w3SendTaskExecutor")` 指向专用线程池（core 10 / max 20 / queue 100 / keepAlive 300s）隔离待办处理与质检主链路；
	2. 分布式幂等设计：`redisTemplate.opsForValue().setIfAbsent(key, key, 10min)` 原子加锁保证同一(用户, 项目, 任务, 待办类型)全局唯一进入，key 粒度精细到单条待办、TTL 10 分钟兜底防死锁、正常退出 finally 删锁放行下次触发；
	3. 分布式幂等设计：发送前锁内二次 `getCheckFlag` 查询业务侧是否仍有未完成任务，防"取锁瞬间业务已流转"导致误发；
	4. 发送结果全程落 `t_quality_inspection_project_w3` 表（INIT → SUCCESS/FAIL），门户调用失败标 FAIL，后续任何状态变动的"双状态对齐"（业务侧活 vs 待办侧残留）自动补偿修复，实现最终一致。



难点 3：Redis 分布式锁 + 独立线程池的 W3 待办异步处理

### Situation（背景）

质检任务派发/复核时，需要向 W3 待办中心（内部待办系统）发送/删除待办。问题：

1. W3 接口响应不稳定，删除操作偶发超时，同步调用会阻塞质检主流程；
2. 并发场景下同一待办可能被重复删除（派发与复核几乎同时触发），重复调用下游产生脏数据；
3. 应用发布重启时，线程池中未完成的待办任务不能直接丢弃。

### Task（任务）

设计异步待办处理机制：发送/删除互不阻塞、删除操作幂等、应用停机时任务可优雅收尾。

### Action（行动）

1. **双线程池隔离**：`send_w3_todo` 与 `del_w3_todo` 各为 `Executors.newFixedThreadPool(5)`，发送慢不影响删除、删除慢不反压发送；
2. **Redis 分布式锁保证幂等**：`deleteW3Todo` 中用 `redisTemplate.opsForValue().setIfAbsent(key, value, expireTime, MILLISECONDS)` 抢锁，key 为 `PRE_TALK_KEY + reviewAccount + taskId + todoType + "_delete_flag"`，TTL 取系统配置 `todo_time`（默认 10 分钟）；抢锁失败说明已有实例在处理，直接 `future.complete(null)` 幂等返回，不重复调下游；
3. **CompletableFuture 编排**：对外返回 `CompletableFuture<Void>`，调用方可链式编排或忽略；
4. **优雅停机**：`@PreDestroy destroy()` 中 `shutdown → awaitTermination(60s) → shutdownNow` 标准三段式，60 秒内未完成的才强制终止。

### Result（结果）

- 待办操作完全异步化，质检主流程 RT 不受 W3 接口影响；
- 并发重复删除被锁拦截，下游无脏数据；
- 发布重启时 60 秒窗口内任务正常收尾，无任务丢失。
