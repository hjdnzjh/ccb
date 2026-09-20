# 计费缴费实现与验证计划

> 执行方式：依照 executing-plans / subagent-driven-development，按任务完成与核验。用户已确认对应设计；保留当前工作目录中已完成的启动改动，不切换或覆盖它们。

**Goal:** 修复出账用量，支持可靠的部分缴费、结清、流水和两个一致的页面入口。

**Architecture:** Java 事务锁定抄表/账单行；MySQL 保存不可变缴费流水和唯一请求标识；Vue 复用收款、详情组件。真实支付网关、权限、自动违约金等保持为后续独立阶段。

**Tech Stack:** Spring Boot 3.2、MyBatis-Plus、MySQL 8、JUnit 5/Mockito、Vue 3/Element Plus、Node 内置测试。

## 接口约定

- `POST /api/v1/bill/pay/{id}`：query 参数 `amount`、`payMethod`（wechat/alipay/bank/cash）、`tradeNo`（必填，1–100 个字母数字或 `_:-`，客户端同一次登记及重试使用同一值）。
- 成功：`{success:true,data:{billId,paidAmount,remainingAmount,status,payment:{id,billId,amount,payMethod,tradeNo,paidTime},replayed}}`。`paidAmount` 为账单累计已付；同请求重放返回原流水及当前账单余额。
- `GET /api/v1/bill/{id}/payments`：`{success:true,data:[{id,billId,amount,payMethod,tradeNo,paidTime}]}`，按时间倒序；缺失账单明确报错。
- 原 `GET /api/v1/bill/{id}` 保留直接返回 Bill 的格式；缺失账单改为业务失败对象。
- 列表新增 `remainingAmount`；汇总含 `remainingAmount`（全部欠款）和 `overdueAmount`（已到期欠款）。前端金额通过十进制字符串/分单位校验，服务端 BigDecimal 最终校验。

## 任务 1：回归测试与出账

- [x] 新建 `water-service/src/test/java/com/water/ai/meter/service/impl/BillServiceImplTest.java`，固定费率，模拟持久层；对真实 service 调用验证 150/30 读数应产出 120→150、30 吨、117.99 元。
- [x] 先执行 Maven 单测，记录旧代码失败。加入阶梯边界、未审核、错配归属、无效用量、重复出账和批量失败计数测试。
- [x] `MeterReadingMapper` 增加按 ID `FOR UPDATE` 查询；`BillMapper` 按 reading_id 查询有效账单；`BillServiceImpl` 先锁抄表、校验、检查既有账单、计算并保存。
- [x] 批量出账通过注入的 Spring 事务模板逐项运行独立事务，逐项统计 created/existing/errors，避免 self-invocation 丢失事务。

## 任务 2：流水和缴费事务

- [x] 新增 `BillPayment`、`BillPaymentMapper`、迁移 `database/migrations/20260917_bill_payment.sql`；新库 `init.sql` 同步创建表。
- [x] 流水列：自增 id、bill_id、DECIMAL(12,2) amount、pay_method、全局唯一 trade_no（ASCII binary 比较）、paid_time，索引 bill_id/id。
- [x] 先测试负数/零/超额/多位小数/渠道/请求号拒绝；40+60 累加、已支付拒绝、同请求重试与内容冲突；再实现事务锁账单、检查重放、写流水及累计更新。
- [x] 同 bill 的并发请求用行锁串行；跨 bill 同请求号由唯一键拒绝并回滚；避免吞掉 SQL 错误造成部分提交。
- [x] 原始欠费/逾期查询覆盖 status 0/2/3；列表和汇总计算剩余应付；新增流水读取接口。

## 任务 3：前端收款入口

- [x] 新建共享 `BillPaymentDialog.vue`、`BillDetailDialog.vue` 和必要金额辅助函数；先用 Node 内置测试覆盖金额边界和剩余计算，再实现辅助函数。
- [x] `Payment.vue` 完成真实搜索、分页、欠款汇总、空态、错误重试、收款及流水查看。
- [x] `bill/List.vue` 复用组件；禁止重复提交，失败保留输入，成功刷新；移除虚假催缴成功提示。
- [x] `api/index.js` 增加流水查询；详情和缴费遵守上面的接口约定。

## 任务 4：数据库与接口集成验收

- [x] 只读检查历史 reading_id 重复账单；存在冲突先列出并停止新流程部署。
- [x] 执行可重复迁移两次，保留业务数据；MySQL 集成测试使用专门测试库或可清理的独立测试数据。
- [x] 验证同抄表并发生成只落一条；两请求并发付款不超收；SQL 故障导致流水和账单同事务回滚；重试返回同流水。
- [x] 测试结束只清理本次明确标识的数据，保持原演示数据。

## 任务 5：交付验证和文档

- [x] `mvn.cmd '-Dmaven.repo.local=../.m2repository' test`；前端 `node --test ...` 和 `npm.cmd run build`。
- [x] 重建 JAR、只重启当前项目后端；验证 3000 页面、8080 健康、真实 API 及 8087 引擎。
- [x] 独立检查方案覆盖、金额与并发一致性、前端错误处理，再修复发现的问题。
- [x] 更新 README 迁移与操作说明、需求清单完成状态；明确本轮只完成账务基础，不把演示登记称为真实网关支付。

## 验收记录（2026-09-17）

- `tools/test-billing.ps1`：15 项 Java 单测、9 项实际 MySQL 集成测试全部通过，0 失败、0 跳过。隔离数据库为 `water_meter_billing_test`。
- 独立审查发现批量出账预读创建旧事务快照的问题；新增确定性回归测试先复现失败，再将批量首读及既有账单查找改为锁定当前读。补充并发批量测试，修复后审查通过。
- Node 金额及请求标识测试 6 项通过；前端生产构建和 Java JAR 打包通过。普通打包默认跳过 opt-in MySQL 集成测试，完整集成结果见上述独立测试运行。
- Edge 使用独立临时数据验证管理员登录、30 吨出账 117.99 元、重复出账、超额拒绝、40 元部分收款、77.99 元结清；模拟服务端提交后响应丢失，刷新后重试只返回原流水，最终两笔流水及零余额。页面无 JavaScript 异常，主内容无横向溢出。
- 迁移执行两次；验收前后开发库均为 27 张原账单、已付合计 1415.88 元；临时验收数据及其流水已清理。
- 最终后端重启健康 `UP`，引擎 `healthy`，前端 3000 及五个中间件正常运行。启动与升级步骤见根目录 README。
- 构建仍有原有 Sass 弃用及资源包体积警告；权限、用户端、自动违约金、自主调度和报表属于后续阶段。
