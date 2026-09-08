# Lucent 升级任务交接报告（HANDOFF REPORT）

> 目标版本 **3.0.0** · 更新于 2026-09-08（最新一次）
>
> 本文档记录升级任务的完整状态：总任务、约束要求、已完成项、当前阻塞点、剩余工作与总体计划。供后续会话（人或 AI）无缝接续。

---

## 1. 总任务

依据 `UPGRADE-PLAN-2.8.md`（注：该文件当前不在仓库中，以下任务清单来自会话上下文记录），将 Lucent 项目从 2.8.x 升级到 **3.0.0**，共分 P0–P3 四个阶段：

| 编号 | 任务 | 说明 |
|---|---|---|
| P0 | 工程/构建基线 | lint baseline、detekt、测试修复、CI 门禁（P0-1..P0-6） |
| P1 | 架构现代化 | P1-1 KMP 模块 + expect/actual、P1-2 AssistantController 状态分离、P1-3 SettingsScreen 双平台分解 |
| P2 | 数据与检索 | P2-1 FTS5 全文搜索、P2-2 语义召回（embedding + 向量检索）、P2-3 ProviderAdapter 拆分、P2-4 构建加固 |
| P3 | 平台能力 | P3-1 Room KMP 原型验证、P3-2 隔离进程（GenerationService）、P3-3 i18n 机械校验 |
| — | 版本号 | 全部子项目版本号升级到 3.0.0 |

---

## 2. 用户要求与约束

1. **一步到位完成所有任务** —— 不要因人力估算而分批搁置，全部 P0–P3 都要完成。
2. **尽量少用本地编译/网络流量** —— 本地沙箱无法运行 Gradle（`Operation not permitted`），**所有验证一律通过 GitHub Actions CI**。
3. **最大轮数调到 500** —— 允许长时间持续工作直到全部完成。
4. **每完成一个任务就 push 到 GitHub 并确保 CI 通过**。
5. **用户已授权 root（--su）**，但能不用就不用；危险操作需先说明后果并获明确同意。
6. **与用户交流使用中文**。

---

## 3. 已完成并已推送（在 main 上）

以下提交均已推送到 `origin/main`（远端 `https://github.com/Yuan0-o/Lucent.git`）：

| Commit | 内容 |
|---|---|
| `f05f55c` | CI lint 门禁 + 已提交的 lint baseline（P0-6） |
| `3272f50` | CI detekt 规则（复杂度 + empty-catch）（P0-6） |
| `aa0d722` | CI 强制 i18n 生成新鲜度（P3-3） |
| `af57e3e` | CI 上报 APK 体积 + 上传 R8 mapping（P2-4） |
| `c0b5dc8` | 抽取 `ProviderAdapter` 密封接口（P2-3） |
| `2c41cca` | lint baseline 提交 + DataKeysTest suspend 修复（P0-6, P2-3） |
| `b077c38` | 测试 context 修复 + manifest key 名匹配 |
| `8ca014c` | 桌面 context 解析修复 + FTS5 schema 迁移（P2-1 部分 1） |
| `a19f37f` | schema 升级到 v18 + FTS5 索引（P2-1 部分 2） |
| `0f651ad` | 迁移后创建索引，修复 legacy 库缺列问题（P2-1） |
| `d682eda` | 测试间重置 DataKeys 缓存（P2-1） |
| `0fe665e` | 添加本交接报告 |
| `a03ab46` | 修复 FTS5 列名、legacy 测试 schema、异常措辞（P2-1） |
| `2646a07` | CI 跳过纯 Markdown 改动 |
| `6ce34e4` | 完成 FTS5 全文检索接入（P2-1，**待 CI 验证**） |

**已完成的具体工作：**
- ✅ **P2-3**：`LlmClient.kt` 拆分为 `ProviderAdapter` 密封接口（OpenAI/Anthropic/Google 三个实现）+ `StreamAccumulator`，LlmClient 仅保留传输与重试逻辑，含单元测试。
- ✅ **P3-3**：`catalog.py` 扩充到 1285 条（+137 缺失条目），支持条件条目生成，CI 检查 i18n 新鲜度。
- ✅ **P2-4**：构建后上报 APK 体积、上传 R8 mapping 作为 CI artifact。
- ✅ **P0-6**：提交 lint baseline（`app/lint-baseline.xml`，约 55KB），CI lint 门禁通过。
- ✅ **测试修复**：修复 4 个预存测试的编译与运行时问题（DataKeys/Db 改用 `filesDir` 直接访问、测试间缓存重置、manifest key 名匹配）。
- ✅ **P2-1（完整）**：FTS5 全文索引 —— 
  - schema v18（`notes_fts`/`tasks_fts` 虚拟表、同步触发器、初始 rebuild）
  - 修复 FTS 列名匹配实际表结构（`title, body` / `title, notes`）
  - 桌面 `searchNotes`/`searchTasks` 优先用 FTS `MATCH`，失败回退 LIKE
  - Android/桌面 DAO 增加 `rebuildFts()` 方法
  - `BackupImport` 导入后自动重建 FTS 索引

---

## 4. 当前阻塞点

最新提交 `6ce34e45` 的 CI 仍有 **2 个失败点**：

### 4.1 `jvm-check` → Run detekt ❌ 失败
- **CI 日志错误**：
  ```
  Property 'buildUponDefaultConfig' is misspelled or does not exist.
  Property 'exceptions>EmptyCatchBlock' is misspelled or does not exist.
  ```
- **根因**：`config/detekt/detekt.yml` 配置格式与 detekt 1.23.8 不兼容：
  - `buildUponDefaultConfig` 是 Gradle 插件属性，不应出现在 YAML 中（已在 `desktop/build.gradle.kts` 第 29 行正确设置）
  - `exceptions` 规则集在 detekt 1.23.x 中不存在；`EmptyCatchBlock` 属于 `empty-blocks` 规则集
- **修复方向**：从 YAML 中删除 `buildUponDefaultConfig: false`、将 `exceptions:` 改为 `empty-blocks:`。

### 4.2 `android-jvm-check` → Run Android JVM unit tests ❌ 失败
- 具体错误信息待获取（CI artifact 未下载成功）
- 可能原因：`app/src/main/java/com/lucent/app/data/Daos.kt` 中新加的 `rebuildFts()` 方法可能导致 Room 编译错误，或者 `@Query` 返回类型需要调整

### 4.3 已修复并确认通过的 3 个桌面测试
- `DbMigrationTest`（FTS5 列名修复 ✅）
- `DbEncryptionTest`（legacy 表补列 ✅）
- `DataKeysTest`（异常消息含 `.lcb` ✅）

> ⚠️ 这 3 个测试在本次会话之前从未真正运行过（此前 CI 在编译阶段就因 `getApplicationContext` 报错而失败），因此是**预存缺陷**，不是本次改动引入的。

---

## 5. 尚未完成的任务

| 编号 | 任务 | 状态 | 依赖 |
|---|---|---|---|
| P1-1 | KMP 模块拆分 + `expect/actual` 重构（约 6 组文件、~7400 行） | 🔴 未开始 | — |
| P1-2 | `AssistantController` 状态与逻辑分离 | 🟡 未开始 | — |
| P1-3 | `SettingsScreen` 双平台分解 | 🟡 未开始 | — |
| P2-2 | 语义召回（embedding + 向量检索） | 🟡 未开始 | P2-1 ✅ |
| P3-1 | Room KMP 原型验证（SQLCipher 兼容性） | 🔵 未开始 | — |
| P3-2 | 隔离进程（`GenerationService`） | 🔵 未开始 | — |
| 版本号 | 全部子项目升级到 3.0.0 | 🟡 未开始 | 收尾时 |

---

## 6. 总体计划（剩余路径）

1. **等待 CI 验证 `6ce34e4`**（P2-1 完整实现），确保 jvm-check 和 android-jvm-check 都通过。
2. **推进 P1 架构现代化**（P1-1 → P1-2 → P1-3），每步提交并验证 CI。
3. **推进 P2-2 语义召回**（embedding + 向量检索）。
4. **推进 P3-1 / P3-2**（Room KMP 验证 / 隔离进程）。
5. **收尾**：全部子项目版本号升到 `3.0.0`，跑全量 CI，确认通过后标记完成。

> 每完成一个可独立验证的里程碑就 `git push`，避免一次性堆积大量未验证改动。

---

## 7. 环境与工具备忘

- **仓库**：`/root/lucent`，分支 `main`；远端 `https://github.com/Yuan0-o/Lucent.git`
- **Git 身份**：`Yuan0-o <235806630+Yuan0-o@users.noreply.github.com>`
- **GitHub Token**：`/root/.dsh/github_token`
- **本地编译**：受限，无法运行 Gradle（沙箱 `Operation not permitted`）；验证靠 GitHub Actions CI。
- **JDK**：17（`/usr/lib/jvm/java-17-openjdk-arm64`）；**Android SDK**：`/opt/android-sdk`（platform 36）
- **CI 检查项**：`jvm-check`（桌面测试 + detekt + i18n 新鲜度）、`android-jvm-check`（Android 测试 + Room schema + lint）
- **桌面测试驱动**：`io.github.willena:sqlite-jdbc`（SQLCipher 方案）；测试里普通 JDBC 用 `jdbc:sqlite:`（org.xerial 风格）

---

## 8. 交接给下一位执行者的第一件事

1. 拉取最新 `main`（当前 HEAD = `6ce34e45`）。
2. 查看 CI 结果（[Actions](https://github.com/Yuan0-o/Lucent/actions)）— 当前已知 2 个失败：
   - **detekt 配置**：修 `config/detekt/detekt.yml`：删 `buildUponDefaultConfig: false`（已在 Gradle 中），改 `exceptions:` 为 `empty-blocks:`。
   - **Android 测试**：查阅 CI 日志确认具体错误，多半是 `app/src/main/java/com/lucent/app/data/Daos.kt` 中 `rebuildFts()` 的 Room 注解问题。
3. 修复后提交、push，等 CI 全绿。
4. CI 全绿后，按计划推进：P1-1 → P1-2 → P1-3 → P2-2 → P3-1 → P3-2 → 版本号收尾。
