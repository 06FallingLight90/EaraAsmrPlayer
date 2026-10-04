# 行为档案：两份 `normalizeRelativePath` 语义不同（勿合并）

> 发现于 R3-A5。两处同名函数**不是**重复实现，语义不同，禁止"相似即合并"。

## 行为描述（两份不同）

| 位置 | 代码 | 语义 |
|---|---|---|
| `util/TrackKeyNormalizer.normalizeRelativePath` | `nfkcLower` → 去空白 → 折叠分隔符 → **去扩展名** | 用于音轨去重键（`buildKey`）：归一化到"无扩展名的相对路径"，含 NFKC 全角折叠与扩展名剥离 |
| `ui/library/albumdetail/AlbumDetailDirectorySupport.normalizeTreeRelativePath`（R3-A5 由 `normalizeRelativePath` 改名） | `replace('\\','/')` → trim → trim('/') → lowercase | 用于目录树本地/远端文件**匹配**：只做斜杠归一与小写，**不**去扩展名、**不**做 NFKC |

## 为什么不能合并

- 目录树匹配依赖扩展名参与比较（`fileName()` 取 `substringAfterLast('/')` 需保留扩展名；`resolvedExtension` 从名字取扩展名）。若换成 `TrackKeyNormalizer` 版，扩展名被剥离会导致匹配与扩展名解析错误。
- 音轨去重依赖扩展名剥离与 NFKC 折叠（全角/半角、大小写统一）。若换成目录树版，去重键会把 `A.MP3` 与 `A.mp3` 视作不同，重复计数。

## 代码位置

- `app/src/main/java/com/asmr/player/util/TrackKeyNormalizer.kt`（`normalizeRelativePath`，被 `buildKey`、`LyricsTargetContext` 调用）
- `app/src/main/java/com/asmr/player/ui/library/albumdetail/AlbumDetailDirectorySupport.kt`（`normalizeTreeRelativePath`，仅本文件调用：`fileName` / `MatchCandidate` / `RemoteCandidate`）

## 钉测试引用

- `app/src/test/java/com/asmr/player/util/TrackKeyNormalizerTest.kt`（`normalizeRelativePath_removesExtensionAndNormalizesSeparators`）钉住 TrackKeyNormalizer 版语义。
- 目录树版暂无单测；改名不改行为（纯重命名）。

## 触发条件

改动任一"相对路径归一化"逻辑时，须确认改的是哪一份；不要跨两份互相替换。
