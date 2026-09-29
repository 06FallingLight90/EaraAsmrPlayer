# Agent 协作约定

> 本文档是给 AI agent 的仓库协作约定（可移植：不含任何本机专属路径）。
> 本机的具体路径、命令与环境状态见 [当日 devnote 笔记](README.md)。
> 仓库的 `AGENTS.md` 已被 .gitignore 忽略 — 不希望提交的私密指令（本机细节、个人偏好）写在那里，希望团队共享的约定写在这里。

## 硬性原则：入库可移植性

**入库文件尽量采用可移植的代码方案；迫不得已涉及本机路径时，明确单独告知用户。**

具体应用：

- 入库的配置（`gradle.properties`、`*.gradle.kts`、CI 工作流）**只含可移植内容** — 机器专属值一律外置到本机配置，并在入库文件中留注释说明约定
- 本机专属配置的三个去处（均不入库）：项目 `local.properties`、Gradle 主目录下的 `gradle.properties`、Gradle 主目录下的 `init.d/` 脚本
- 为什么严格：本仓库 CI（`.github/workflows/release.yml`）在 Linux 上构建，入库文件中的 Windows 路径会直接挂 CI；其他贡献者的机器同理
- 涉及本机路径的改动完成时，**单独**向用户报告涉及了哪些本机路径，与常规改动汇报分开

## 构建验证循环

改动代码后按此循环验证，每步有明确完成标准：

1. **构建** — 执行项目约定的构建命令（见当日 devnote）→ 完成标准：`BUILD SUCCESSFUL`
2. **安装** — 安装到实机（`installDebug` 或 `adb install -r`）→ 完成标准：输出 `Success`；小米设备留意手机端弹窗确认
3. **启动验证** — 启动应用并确认前台 → 完成标准：`dumpsys activity activities` 中 `MainActivity` 位于 `topResumedActivity`
4. **日志** — `adb logcat --pid=$(adb shell pidof com.asmr.player)` → 完成标准：目标功能的行为有日志佐证

长构建用后台运行 + 日志文件跟踪；构建失败时先读 daemon 日志定位（现象与根因的对应关系见踩坑记录），再重试。

## 文件分级约定

写文件前先判断归属：

- **入库**：源码、构建脚本、`docs/`（含本目录）— 必须可移植，不含本机路径
- **本机**：`.gitignore` 覆盖的路径（以 [.gitignore](../../.gitignore) 为准）— 本机专属配置、缓存、密钥放这里

新建文件若两类都不属于（如临时脚本、分析产物），先归入本机路径并记录到 devnote，避免污染仓库。

## Agent 运行环境注意事项

以下针对 Trae 沙箱环境下的 Windows 开发：

- **项目目录外的写入可能被沙箱拦截**：换已批准的同级路径重试一次；仍拦截则请用户添加沙箱规则，勿反复尝试
- **PowerShell 写配置文件用无 BOM 方式**：`Set-Content` 默认带 UTF-8 BOM，会静默破坏 Java Properties 首个键（历史坑：JDK 配置失效、构建报裸版本号）。用 Write 工具或 `[IO.File]::WriteAllText` + `UTF8Encoding($false)`
- **`cmd /c` 被禁用**：用 PowerShell 原生语法替代（如管道应答用 `$items | & command`）
- **Java Properties 文件写入后校验编码**：`Format-Hex` 查看前 3 字节是否为 `EF BB BF`

## 新增文件记录

开发过程新增的非源码文件、本机环境变更、踩坑解法 → 记入 [devnote](README.md) 当日笔记（格式见其记录约定）。
