# 2026-10-07 — R3-C6 结构收尾（三片）

> 承接 [2026-10-07-r3-c8-close.md](2026-10-07-r3-c8-close.md)（C8 决策与 C5 降级）。

## C6-1 scanFromDocumentTree 拆函数（14a62c9）

- **取证修正**：plan 点名的 `walkTree` 经 C1c-b 迁移后已是 `SafTreeSupport.walkTree` 的 2 行 context 委托（LibraryScanStateHolder L245），**无拆分需求**——计划行文写于迁移前；实际对象只有 `scanFromDocumentTree`（原 137 行单 suspend 函数）。
- **拆法**：主壳 ~27 行（uri 解析/children 过滤/prune 对账收尾）+ `scanSingleDocumentAlbum`（per-album 体逐字随迁，返回 albumPath 供 prune）+ 3 个私有函数（`buildDocumentTrackSpecs`、`buildDocumentAlbumEntity`、`backfillDocumentAlbumCover`，逐字随迁）。
- **一处非逐字适配**：原 `audioExtensions`/`subtitleExtensions` 是 forEach 外的一次性局部 val，拆分后改为 `documentAudioExtensions()`/`documentSubtitleExtensions()` 成员函数（纯集合构造，行为等价；避免跨函数传参膨胀）。
- 验证：compileDebugKotlin ✓、全量 994/0/4 ✓。

## C6-2 BottomChrome 概念归包（8140ad7）

- **取证**：BottomChrome.kt（1378 行，ui/nav 包）的消费者 **100% 在 main 包**（8 文件 44 处 import）；main 包另有 MainChromeUi.kt（Drawer 内容，名字带 Chrome 但与 BottomChrome 无符号交集）。
- **做法**：`git mv` ui/nav/BottomChrome.kt → main/；两个同包测试（BottomChromeTest/BottomChromeWidthTest）随迁 test/.../main/；7 个 main 包消费者删 import、MainActivity（根包）改 main.*、MainOverlayUi 的 FQN 参数类型同步改。
- **踩坑 41**：**同包符号移包后"隐式可见"变"必须显式 import"**——BottomChrome.kt 与其测试原来无 import 直接用 `Routes`（AppNavigator.kt，ui/nav 包定义），移包后满屏 Unresolved reference: Routes；两文件各补一条 `import com.asmr.player.ui.nav.Routes` 即修复。移包类任务需先 grep 目标符号集是否引用了原包其他符号。
- **踩坑 42**：**FQN 内联引用躲过 import 清理**——MainOverlayUi 以 `List<com.asmr.player.ui.nav.BottomChromeNavItem>` 参数 FQN 写法引用（非 import 语句），按 import 行清理会漏；移包后须全仓 grep 旧包路径 FQ 引用而非只看 import。
- **baseline**：死条目 ×2 移除（ui/nav/BottomChrome.kt → ui.player.MiniPlayer/MiniPlayerDisplayMode）；**main/BottomChrome.kt → ui.player.* 不触守卫**（main→ui.player 非禁令方向），零新增条目。ci_guard + 自检 ✓。
- **顺带**：ui/nav 包现在只剩 AlbumCoverHintStore.kt + AppNavigator.kt（导航辅助件），BottomChrome 归位后 Chrome UI 概念聚拢 main 包。

## C6-3 dao 投影 DTO 归位（7e6deb9）

- **取证**：dao 包混入 5 个查询投影 DTO：AlbumTagsCsv、DownloadTaskWithItems（独立文件）、TrackTagsCsv（TrackTagDao.kt 内）、TagDurationRow+HourDurationRow（ListeningSessionDao.kt 内）。消费者全在 data/repository（3 处 import）+ dao 自身——data→data 移动零新违规。
- **做法**：新建 `data/local/db/projection/` 包：git mv 两个独立文件 + 3 个内嵌类提出为独立文件（TrackTagsCsv.kt、ListeningSessionProjections.kt 合并两行投影）；4 个 DAO 文件删内嵌类 + 补 import；2 个 repository import 重键。
- **踩坑 43**：**baseline-prof.txt 的 FQ 需随移包同步**——DownloadTaskWithItems 以 `Lcom/asmr/player/data/local/db/dao/DownloadTaskWithItems;` 形式出现在 startup profile 5 处；profile 指向不存在类只是静默失效不报错，须手动 grep `com/asmr/player/<旧包路径>` 同步（本片 ×5）。
- 提交纪律备注：AlbumTagsCsv/DownloadTaskWithItems 两个 git mv 因暂存区连带随 C6-1 提交（rename 100% 无内容差异），C6-3 提交含其余归位改动——分片纯净度小瑕疵，如实记录。
- 验证：compileDebugKotlin ✓、定向 Chrome/nav 5 测类 ✓、全量 994/0/4 ✓、ci_guard + 自检 ✓。

## 状态

C6 三片收官（14a62c9 / 8140ad7 / 7e6deb9）。plan §8.4 队列剩余：C9（搜索重写，未开工）→ 阶段门禁（全量测试双绿 + 子代理审查 + 实机走查）→ tag `refactor-r3/phase-C`。C9 与门禁顺序待用户确认。
