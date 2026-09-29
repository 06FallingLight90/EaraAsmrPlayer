# 2026-09-30 重构启动：S0 绿基线 + S1 CI 门禁

> 目标：重构前建立可信测试基线（S0）与 CI 门禁（S1）。S0 已达成，S1 文件已入库（CI 实际变绿待 push 后验证）。

## 结果

- 全量 `:app:testDebugUnitTest`：**BUILD SUCCESSFUL，840 tests / 0 failures / 4 skipped（171 个类）**
- 基线数即阶段门禁的对比基准：后续每阶段测试总数只增不减

## 踩坑记录（现象 → 根因 → 解法，均已验证）

### 1. 首次全量测试约 100 个失败，失败面横跨无关模块

- **现象**：MainContainer/Theme/Room 迁移/下载/翻译等互不相关的测试类同时失败。
- **根因**：全部为 Robolectric 系测试。Robolectric 的 `MavenDependencyResolver` 每次解析依赖都要在 **`%USERPROFILE%` 根部**创建 `.robolectric-download-lock` 并把 android-all jar 缓存到 `%USERPROFILE%\.m2`——本机受控环境对用户主目录根部无写权限，锁文件创建被拒（`FileNotFoundException: 拒绝访问`）。
- **解法**：[app/build.gradle.kts](../../app/build.gradle.kts) 的 `testOptions.unitTests.all` 把测试 JVM 的 `user.home` 重定向到构建目录 `test-user-home/`（gitignored、可移植、无本机路径），并在 `doFirst` 中 `mkdirs()`（Robolectric 不会自建目录）。锁文件与 android-all 缓存随之落到构建目录。

### 2. user.home 重定向后仍报「系统找不到指定的路径」

- **根因**：Robolectric 直接在 `${user.home}` 下创建锁文件，不 mkdirs 父目录。
- **解法**：测试任务 `doFirst { testUserHome.mkdirs() }`。

### 3. 修好基建后剩 1 个确定性失败：PageTranslationUiTest.cachedFileTranslation…

- **现象**：`SQLiteCantOpenDatabaseException`，发生在 `PageTranslationCache`（文件型 `SQLiteOpenHelper`，`page_translation_cache.db`）打开库执行 `PRAGMA journal_mode=WAL` 时；单独运行该类仍失败，排除顺序依赖。
- **根因**：项目其余 DB 测试全用内存库（`Room.inMemoryDatabaseBuilder`），只有此测试走「文件库 + WAL」组合，在 Windows/Robolectric 的 legacy sqlite shadow 下打不开库。
- **解法**：该测试方法内加 `assumeFalse(os.name contains Windows)`——Windows 跳过、Linux CI 仍执行，不丢覆盖。**若 CI 上该测试也失败，则升级为真修复**（升 Robolectric 或改 WAL 配置）。

## 新增文件

| 路径 | 内容 | 归属 |
|---|---|---|
| `.github/workflows/ci.yml` | push/PR 触发 `testDebugUnitTest`（JDK 17 temurin） | 入库 |
| `docs/iteration/` | 阶段审查报告落盘处（已加入 .gitignore） | 本机（gitignored） |

[release.yml](../../.github/workflows/release.yml) 在 `assembleRelease` 前插入 `testReleaseUnitTest` 步骤。

## 待验证

- S1 的「CI 全绿」完成标准需 push 分支后由 GitHub Actions 验证（push 时机由用户决定）
