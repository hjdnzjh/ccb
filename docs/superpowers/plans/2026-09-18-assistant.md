# 水务助手修复实施计划

**目标：** 修复现有助手的答非所问与随机数据，提供有证据、可复核的业务回答。

**结构：** Spring Boot 的 assistant 包负责只读查询和答案；OpsService 委托；Vue 渲染结构化卡片，沿用当前鉴权。

- [x] 添加原问题回归测试，确认旧接口返回模拟报表而非巡检清单。
- [x] 增加 AssistantRepository 与 AssistantService，接入 OpsService；覆盖四个业务意图及边界。
- [x] 更新 Assistant.vue 的证据、表格、范围、时间、能力提示、操作入口和中文输入行为。
- [x] 测试 MySQL 查询、单元测试和前端构建；更新启动文档。
- [x] 重启项目后端，浏览器复验原问题与其他场景，记录验证结果。

## 验证记录

- 旧版接口对原巡检问题缺少 intent/rows，浏览器 API 回归断言失败；修复后通过。
- 条件回归先复现“A区和Z区”被忽略问题，后增加保守语法校验；个人条件、自定义阈值及未支持日期都明确澄清。
- 67项 Java 测试通过，零失败/跳过；助手新增9项单元和3项MySQL集成测试。日志 `logs/assistant-full-tests.log`。
- 独立只读审查发现同期比较被误拒绝，补充完整比较句式及回归用例后通过。
- 后端打包、前端生产构建通过；后端已重新启动并健康检查通过。
- Edge实际页面验证原问题、稳定结果、区域漏水、收入、未知条件、清空对话、中文输入法、错误后重试和手机宽度无横向溢出。
- 浏览器脚本 `logs/browser-check/assistant.cjs`；截图 `logs/assistant-inspection-fixed.png`、`logs/assistant-mobile-fixed.png`。业务数据未被问答接口修改。
