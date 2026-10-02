#!/usr/bin/env python3
"""架构守护（R2-A1 重写，修 20261001 体检 P0-3 三盲区）：
1. 单文件行数上限：>SIZE_LIMIT 行禁入（ratchet——存量超限文件以
   "路径: 行数上限" 格式钉死在 tools/size-guard-baseline.txt，新增超限即失败；
   存量修复后须同步收缩 baseline，防止回潮）。
2. import 方向：按文件**真实 package 行**匹配源（目录=包名已在 R2-A2 对齐），
   全仓扫描（不再只扫 /data/ 目录）。规则见 RULES。存量违规以 "<rel> <fq>"
   记录在 tools/import-direction-baseline.txt（ratchet：新增即失败）。
3. 规则自检：tools/guard-selftest/<规则名>/ 下的反例夹具必须被对应规则命中，
   防止规则再次空转（体检发现 main.* 前缀规则永不命中的教训）。

用法：python3 tools/ci_guard.py（仓库根目录运行）
"""
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "app" / "src" / "main" / "java"
SELFTEST = Path(__file__).resolve().parent / "guard-selftest"
SIZE_LIMIT = 1500
# pin 配额松弛容差：cap 超出实测行数这么多即判定"还债未同步收缩 baseline"，
# 失败提示重写 cap（20261002 体检 Quick Win #1：ratchet 要推动下降，不只防回潮）
SIZE_SLACK_TOLERANCE = 50
SIZE_BASELINE = Path(__file__).resolve().parent / "size-guard-baseline.txt"
IMPORT_BASELINE = Path(__file__).resolve().parent / "import-direction-baseline.txt"

# 根包留守的入口类型：service/subtitle 直接 import 即构成 ui(main)↔service 环
ROOT_ENTRY_TYPES = (
    "com.asmr.player.MainActivity",
    "com.asmr.player.AsmrApp",
    "com.asmr.player.ThemeStartupSupport",
)

# feature-to-feature 白名单：ui.common / ui.theme 是跨特征共享层，任何 ui.* 可引
UI_FEATURE_WHITELIST = ("common", "theme")


def ui_feature(fq: str) -> str:
    """取 com.asmr.player.ui.<feature> 的 feature 段；非 ui 包或 ui 根返回 ''。"""
    parts = fq.split(".")
    return parts[4] if len(parts) > 4 else ""

# (规则名, 源包前缀元组, 禁止的导入前缀元组)
# feature-to-feature 例外：目标 ui.common/theme 放行、同特征放行（见 match_imports）
RULES = [
    ("data-to-upper",
     ("com.asmr.player.data",),
     ("com.asmr.player.playback.", "com.asmr.player.ui.", "com.asmr.player.main.")),
    ("data-to-feature",
     ("com.asmr.player.data",),
     ("com.asmr.player.listentogether.", "com.asmr.player.hotlistening.",
      "com.asmr.player.subtitle.", "com.asmr.player.translation.",
      "com.asmr.player.performance.", "com.asmr.player.benchmark.")),
    ("ui-to-dao",
     ("com.asmr.player.ui",),
     ("com.asmr.player.data.local.db.dao.", "com.asmr.player.data.local.db.AppDatabaseProvider")),
    ("ui-to-data-remote",
     ("com.asmr.player.ui",),
     ("com.asmr.player.data.remote.",)),
    ("feature-to-feature",
     ("com.asmr.player.ui",),
     ("com.asmr.player.ui.",)),
    ("ui-to-net-stack",
     ("com.asmr.player.ui",),
     ("okhttp3.", "retrofit2.", "com.google.gson.")),
    ("ui-to-service",
     ("com.asmr.player.ui",),
     ("com.asmr.player.service.",)),
    ("service-to-ui",
     ("com.asmr.player.service",),
     ("com.asmr.player.ui.",)),
    ("service-to-root-entry",
     ("com.asmr.player.service",),
     ROOT_ENTRY_TYPES),
    ("subtitle-to-ui",
     ("com.asmr.player.subtitle",),
     ("com.asmr.player.ui.",)),
    ("subtitle-to-root-entry",
     ("com.asmr.player.subtitle",),
     ROOT_ENTRY_TYPES),
    ("playback-to-ui",
     ("com.asmr.player.playback",),
     ("com.asmr.player.ui.",)),
    ("playback-to-service",
     ("com.asmr.player.playback",),
     ("com.asmr.player.service.",)),
]


def read_lines(path: Path):
    with path.open(encoding="utf-8", errors="replace") as fh:
        return fh.readlines()


def file_package(lines) -> str:
    for line in lines:
        s = line.strip()
        if s.startswith("package "):
            return s[len("package "):].strip()
    return ""


def read_size_baseline() -> dict:
    pins = {}
    if not SIZE_BASELINE.exists():
        return pins
    for raw in SIZE_BASELINE.read_text(encoding="utf-8").splitlines():
        entry = raw.strip()
        if not entry:
            continue
        if ":" not in entry:
            raise SystemExit(f"[size] baseline 已升级为 '路径: 行数上限' 格式，请迁移该行: {entry}")
        rel, _, cap = entry.rpartition(":")
        pins[rel.strip()] = int(cap)
    return pins


def read_import_baseline() -> set:
    if not IMPORT_BASELINE.exists():
        return set()
    return {
        line.strip()
        for line in IMPORT_BASELINE.read_text(encoding="utf-8").splitlines()
        if line.strip()
    }


def size_pin_failures(line_count: int, cap: int) -> list:
    """单个 size pin 的判定逻辑（独立成函数供 selftest 复用）。"""
    problems = []
    if line_count > cap:
        problems.append(f"已超 baseline 上限（{line_count} > {cap}），禁止继续增长")
    elif cap - line_count > SIZE_SLACK_TOLERANCE:
        problems.append(
            f"baseline 配额松弛 {cap - line_count} 行（cap {cap}，实测 {line_count}，"
            f"容差 {SIZE_SLACK_TOLERANCE}），请收缩 baseline 使 cap 贴合实测")
    return problems


def match_imports(rel: str, pkg: str, lines) -> list:
    """返回该文件触发的违规 [(rule, rel, line_no, fq)]。"""
    hits = []
    for rule, src_prefixes, forbidden in RULES:
        if not any(pkg == p or pkg.startswith(p) for p in src_prefixes):
            continue
        for no, line in enumerate(lines, start=1):
            s = line.strip()
            if not s.startswith("import "):
                continue
            fq = s[len("import "):].strip()
            if not any(fq.startswith(p) for p in forbidden):
                continue
            if rule == "feature-to-feature" and (
                ui_feature(fq) in UI_FEATURE_WHITELIST
                or ui_feature(fq) == ui_feature(pkg)
            ):
                continue
            hits.append((rule, rel, no, fq))
    return hits


def check_dir(root: Path, import_baseline: set):
    """扫描目录，返回 (failures, baseline_hits)。"""
    failures, baseline_hits = [], []
    if not root.exists():
        return failures, baseline_hits
    for f in sorted(root.rglob("*.kt")):
        rel = f.relative_to(REPO).as_posix()
        lines = read_lines(f)
        pkg = file_package(lines)
        for rule, _rel, no, fq in match_imports(rel, pkg, lines):
            entry = f"{rel}:{no} {fq}"
            short = f"{rel} {fq}"
            if entry in import_baseline or short in import_baseline:
                baseline_hits.append(f"[import] 存量违规（baseline）：{entry}")
            else:
                failures.append(f"[import] {rule} 违规 {entry}")
    return failures, baseline_hits


def selftest() -> list:
    """每条规则必须命中至少一个反例夹具，防规则空转。"""
    problems = []
    for rule, src_prefixes, forbidden in RULES:
        fixture_dir = SELFTEST / rule
        caught = False
        if fixture_dir.exists():
            for f in sorted(fixture_dir.rglob("*.kt")):
                lines = read_lines(f)
                pkg = file_package(lines)
                if not any(pkg == p or pkg.startswith(p) for p in src_prefixes):
                    problems.append(f"[selftest] {rule}: 夹具 {f.name} 的 package 不属于该规则源前缀")
                    continue
                if match_imports("selftest/" + f.name, pkg, lines):
                    caught = True
        if not caught:
            problems.append(f"[selftest] 规则 {rule} 未命中任何反例夹具（tools/guard-selftest/{rule}/）")
    return problems


def selftest_size() -> list:
    """size pin 判定逻辑的场景自检（无夹具文件，纯合成数据）。"""
    problems = []
    if not size_pin_failures(1200, 1100):
        problems.append("[selftest] size: 超上限场景未失败")
    if not size_pin_failures(1000, 1100):
        problems.append("[selftest] size: 配额松弛场景未失败")
    if size_pin_failures(1060, 1100):
        problems.append("[selftest] size: 容差内场景误报")
    if size_pin_failures(1100, 1100):
        problems.append("[selftest] size: 贴线场景误报")
    return problems


def main() -> int:
    failures = []

    # --- 规则自检（防空转）---
    failures.extend(selftest())
    failures.extend(selftest_size())

    # --- 行数守护（"路径: 行数上限" pin）---
    size_pins = read_size_baseline()
    pinned_seen = set()
    for f in sorted(SRC.rglob("*.kt")):
        rel = f.relative_to(REPO).as_posix()
        line_count = len(read_lines(f))
        if line_count > SIZE_LIMIT:
            if rel in size_pins:
                pinned_seen.add(rel)
                for problem in size_pin_failures(line_count, size_pins[rel]):
                    failures.append(f"[size] {rel} {problem}")
            else:
                failures.append(f"[size] 新增超限文件 {rel}（{line_count} 行 > {SIZE_LIMIT}），禁止入库")
    for rel in sorted(set(size_pins) - pinned_seen):
        print(f"[size] 存量超限文件已修复，请从 baseline 移除：{rel}")

    # --- import 方向守护（真实包名，全仓）---
    import_baseline = read_import_baseline()
    failures_, baseline_hits = check_dir(SRC, import_baseline)
    failures.extend(failures_)
    for h in baseline_hits:
        print(h)

    if failures:
        print("\n=== 架构守护失败 ===")
        for f_ in failures:
            print(f_)
        return 1
    print("架构守护通过（含规则自检）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
