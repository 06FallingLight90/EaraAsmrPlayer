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
SIZE_BASELINE = Path(__file__).resolve().parent / "size-guard-baseline.txt"
IMPORT_BASELINE = Path(__file__).resolve().parent / "import-direction-baseline.txt"

# 根包留守的入口类型：service/subtitle 直接 import 即构成 ui(main)↔service 环
ROOT_ENTRY_TYPES = (
    "com.asmr.player.MainActivity",
    "com.asmr.player.AsmrApp",
    "com.asmr.player.ThemeStartupSupport",
)

# (规则名, 源包前缀元组, 禁止的导入前缀元组)
RULES = [
    ("data-to-upper",
     ("com.asmr.player.data",),
     ("com.asmr.player.playback.", "com.asmr.player.ui.", "com.asmr.player.main.")),
    ("ui-to-dao",
     ("com.asmr.player.ui",),
     ("com.asmr.player.data.local.db.dao.",)),
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
            if any(fq.startswith(p) for p in forbidden):
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


def main() -> int:
    failures = []

    # --- 规则自检（防空转）---
    failures.extend(selftest())

    # --- 行数守护（"路径: 行数上限" pin）---
    size_pins = read_size_baseline()
    pinned_seen = set()
    for f in sorted(SRC.rglob("*.kt")):
        rel = f.relative_to(REPO).as_posix()
        line_count = len(read_lines(f))
        if line_count > SIZE_LIMIT:
            if rel in size_pins:
                pinned_seen.add(rel)
                if line_count > size_pins[rel]:
                    failures.append(
                        f"[size] {rel} 已超 baseline 上限（{line_count} > {size_pins[rel]}），禁止继续增长")
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
