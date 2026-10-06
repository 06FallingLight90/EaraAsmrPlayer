# 2026-10-06 — R3-C7 service 拆解（DownloadManager / SubtitleTaskService / PlaybackService）

## 任务

C7 service 层 God 拆解：`data/download/DownloadManager`（C7-1，1184→516+2）、`subtitle/SubtitleTaskService`（C7-2，1430→204+4）、`service/PlaybackService`（C7-3，1471→765+5）。C7 至此 3/3 收官。

## 新增非源码文件（本机 %TEMP%，不入库）

- `C:\Users\24131\AppData\Local\Temp\c7_dl_slice.py` — DownloadManager 切片（内容锚定行号 + 括号平衡自检）
- `C:\Users\24131\AppData\Local\Temp\c7_sub_analyze.py` — SubtitleTaskService 切片引用分析（成员/import/常量按区间扫描）
- `C:\Users\24131\AppData\Local\Temp\c7_sub_slice.py` — SubtitleTaskService 切片（成员→顶层扩展函数转换、companion 常量限定、private→internal 放宽、import 自动重推导）
- `C:\Users\24131\AppData\Local\Temp\c7_pb_slice.py` — PlaybackService 切片（同 c7_sub_slice 机制 + plain 顶层扩展（PlaybackException/MediaItem 接收者）去缩进 + dry-run 预览模式 + 主文件空行收敛）
- `C:\Users\24131\AppData\Local\Temp\c7-1-msg.txt` / `c7-2-msg.txt` / `c7-3-msg.txt` — 提交消息文件（PowerShell 不支持 heredoc，`git commit -F` 用）

## 踩坑与解法

1. **PowerShell 不支持 heredoc**：`git commit -m "$(cat <<'EOF'...)"` 直接语法错误——改写消息文件后 `git commit -F`。
2. **切片脚本断言先于切片跑**：锚点相对顺序断言写错（companion 与 reqclient 顺序颠倒）会误杀正确定位；主文件区间组装错误（切片 B 尾部多带）要靠 `git checkout -- <file>` 恢复后重跑——脚本写主文件前先打印行数可提前暴露。
3. **`lateinit var` 字段放宽正则**：`private (val|var)` 匹配不到 `private lateinit var`——需 `private (lateinit var|val|var)`；companion 常量放宽要限定 8 空格缩进，否则 4 空格缩进的 `private val networkCallback`（类字段）被误放宽。
4. **字符串模板内 `$CONST` 会漏 import 推导**：`"翻译中 $translating/$DEEPSEEK_TRANSLATION_CONCURRENCY"` 的符号在字符串感知剥离（strip）后不可见，推导出的 import 缺失——编译期 `Unresolved reference` 兜住后手工补。
5. **继承常量在扩展函数内需限定**：成员函数里的 `stopForeground(STOP_FOREGROUND_REMOVE)` 转为扩展函数后失去继承作用域，须改 `Service.STOP_FOREGROUND_REMOVE`。
6. **`max(` 计数为 0 的 import 是纯 stale**：DownloadManager 主文件 11 条历史 stale import（DlsiteAuthStore/NetworkHeaders/coroutines×5 等）按"符号在保留体出现与否"清除。
7. **companion 常量 + 顶层扩展函数**：搬移体内常量统一加 `SubtitleTaskService.` 限定（字符串感知替换，字面量零触碰），规避"扩展函数内 unqualified companion 成员是否解析"的语义不确定性，同时放宽常量 private→internal。
8. **编译缓存快照缺失警告**：`Failed to restore task outputs ... 0.zip` 为 Gradle 缓存恢复告警，自动降级全量重编，非代码问题。

## C7-3 增量（PlaybackService）

9. **属性初始化器调用的成员函数预防性留守**：监听器字段（audioFocusChangeListener/outputBroadcastReceiver/audioDeviceCallback）的初始化 lambda 与 spectrumOutputBufferSizeProvider 回调内调用的 5 个成员（4 handler + updateSpectrumVisualDelay）不转扩展——属性初始化器语境下无括号调用的接收者解析存在语义不确定性，留成员零风险（C7-2"留守函数"先例同型决策，非编译失败实测）。
10. **继承常量 AUDIO_SERVICE（坑 5 同型实测）**：`getSystemService(AUDIO_SERVICE)` 的 AUDIO_SERVICE 是 Context 继承常量，扩展体内失去继承作用域，需 `Context.AUDIO_SERVICE` 限定 + 补 import android.content.Context。
11. **编译器同型错误只报部分**：5 处 AUDIO_SERVICE 未限定只报了 4 处（CLIXML 错误流还有逐行截断换行）——修同型错误时按 grep 全量计数处理，勿按报错条数。
12. **PowerShell `Set-Content -Encoding UTF8` 带 BOM**（总纲 §5 已录，本次实测复现）：行内正则替换后落盘引入 EF BB BF——用 `[IO.File]::WriteAllText($p, $t, (New-Object System.Text.UTF8Encoding($false)))` 重写消除。
13. **区间删除后主文件残留连续空行**：函数区间被移除后其前后空行叠加成 2-6 连空——收尾按原文件风格（单空行）收敛 blank-run 到 1，纯空白变更不需重跑全量测试（括号平衡复查即可）。
