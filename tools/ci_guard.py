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
SCC_BASELINE = Path(__file__).resolve().parent / "package-scc-baseline.txt"

# 根包留守的入口类型：service/subtitle 直接 import 即构成 ui(main)↔service 环
ROOT_ENTRY_TYPES = (
    "com.asmr.player.MainActivity",
    "com.asmr.player.AsmrApp",
    "com.asmr.player.ThemeStartupSupport",
)

# feature-to-feature 白名单：ui.common / ui.theme / ui.translation 是跨特征共享层，任何 ui.* 可引
UI_FEATURE_WHITELIST = ("common", "theme", "translation")


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
    ("ui-to-db",
     ("com.asmr.player.ui", "com.asmr.player.main"),
     ("com.asmr.player.data.local.db.",)),
    ("ui-to-datastore",
     ("com.asmr.player.ui", "com.asmr.player.main"),
     ("com.asmr.player.data.local.datastore.",)),
    ("ui-to-cache-work",
     ("com.asmr.player.ui", "com.asmr.player.main"),
     ("com.asmr.player.cache.", "com.asmr.player.work.")),
    ("ui-to-data-remote",
     ("com.asmr.player.ui", "com.asmr.player.main"),
     ("com.asmr.player.data.remote.",)),
    ("feature-to-feature",
     ("com.asmr.player.ui",),
     ("com.asmr.player.ui.",)),
    ("ui-to-net-stack",
     ("com.asmr.player.ui",),
     ("okhttp3.", "retrofit2.", "com.google.gson.")),
    ("ui-to-service",
     ("com.asmr.player.ui", "com.asmr.player.main"),
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
    ("translation-to-ui",
     ("com.asmr.player.translation",),
     ("com.asmr.player.ui.",)),
    ("hotlistening-to-ui",
     ("com.asmr.player.hotlistening",),
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


def collect_violations(root: Path):
    """扫描目录，返回所有 (rule, rel, line_no, fq) 违规（供 check_dir 与 refresh 复用）。"""
    out = []
    if not root.exists():
        return out
    for f in sorted(root.rglob("*.kt")):
        rel = f.relative_to(REPO).as_posix()
        lines = read_lines(f)
        pkg = file_package(lines)
        out.extend(match_imports(rel, pkg, lines))
    return out


def check_dir(violations, import_baseline: set):
    """按当前违规与 baseline 比对，返回 (failures, baseline_hits)。"""
    failures, baseline_hits = [], []
    for rule, rel, no, fq in violations:
        entry = f"{rel}:{no} {fq}"
        short = f"{rel} {fq}"
        if entry in import_baseline or short in import_baseline:
            baseline_hits.append(f"[import] 存量违规（baseline）：{entry}")
        else:
            failures.append(f"[import] {rule} 违规 {entry}")
    return failures, baseline_hits


def violation_keys(violations) -> set:
    """当前违规的可匹配键（含 'rel:no fq' 与 'rel fq' 两种形式）。"""
    keys = set()
    for _rule, rel, no, fq in violations:
        keys.add(f"{rel}:{no} {fq}")
        keys.add(f"{rel} {fq}")
    return keys


def check_dead_entries(import_baseline: set, violations) -> list:
    """失效条目检测：baseline 里已不再构成当前违规的条目（配额虚高）须移除。

    两类失效：① 导入已删除/文件已不存在；② 规则或白名单变更后该导入不再违规。
    拆文件仅改行号不算失效——匹配键含 "<rel> <fq>" 短形式。
    """
    keys = violation_keys(violations)
    problems = []
    for entry in sorted(import_baseline):
        if entry not in keys:
            problems.append(f"[import] baseline 失效条目（不再违规/导入已移除，请移除）：{entry}")
    return problems


def known_packages(root: Path) -> set:
    """收集目录下所有 .kt 的真实 package 名（供 import → 包 的最长前缀解析）。"""
    pkgs = set()
    if not root.exists():
        return pkgs
    for f in sorted(root.rglob("*.kt")):
        pkg = file_package(read_lines(f))
        if pkg:
            pkgs.add(pkg)
    return pkgs


def resolve_package(fq: str, pkgs: set):
    """把 import 的 FQ 名解析到已知包（取最长前缀）；非本项目返回 None。"""
    if not fq.startswith("com.asmr.player"):
        return None
    parts = fq.split(".")
    for i in range(len(parts), 0, -1):
        cand = ".".join(parts[:i])
        if cand in pkgs:
            return cand
    return None


def build_package_graph(root: Path) -> dict:
    """构建包级有向图（含自环之外的包间边）。"""
    pkgs = known_packages(root)
    graph = {p: set() for p in pkgs}
    if not root.exists():
        return graph
    for f in sorted(root.rglob("*.kt")):
        lines = read_lines(f)
        src = file_package(lines)
        if not src:
            continue
        for line in lines:
            s = line.strip()
            if not s.startswith("import "):
                continue
            fq = s[len("import "):].strip()
            if fq.endswith(".*"):
                fq = fq[:-2]
            tgt = resolve_package(fq, pkgs)
            if tgt and tgt != src:
                graph[src].add(tgt)
    return graph


def tarjan_scc(graph: dict) -> list:
    """Tarjan 求强连通分量（节点数 ≤ 数十，递归安全）。"""
    counter = [0]
    stack, on_stack = [], set()
    index, low, result = {}, {}, []

    def strongconnect(v):
        index[v] = low[v] = counter[0]
        counter[0] += 1
        stack.append(v)
        on_stack.add(v)
        for w in graph.get(v, ()):
            if w not in index:
                strongconnect(w)
                low[v] = min(low[v], low[w])
            elif w in on_stack:
                low[v] = min(low[v], index[w])
        if low[v] == index[v]:
            comp = []
            while True:
                w = stack.pop()
                on_stack.discard(w)
                comp.append(w)
                if w == v:
                    break
            result.append(comp)

    for v in graph:
        if v not in index:
            strongconnect(v)
    return result


def read_scc_baseline() -> int:
    """读包级 SCC 规模上界（max_scc_size=<int>）；缺省 0 表示未设。"""
    if not SCC_BASELINE.exists():
        return 0
    for raw in SCC_BASELINE.read_text(encoding="utf-8").splitlines():
        s = raw.strip()
        if not s or s.startswith("#"):
            continue
        if s.startswith("max_scc_size"):
            return int(s.split("=", 1)[1].strip())
    return 0


def biggest_scc(graph: dict) -> list:
    comps = tarjan_scc(graph)
    return max(comps, key=len) if comps else []


def check_scc(root: Path, limit: int):
    """包级 SCC ratchet：只许减不许增。返回 (failures, notes)。"""
    failures, notes = [], []
    members = biggest_scc(build_package_graph(root))
    size = len(members)
    if limit <= 0:
        notes.append(f"[scc] baseline 未设（当前最大连通团 {size} 包），请写入 {SCC_BASELINE.name}")
    elif size > limit:
        sample = ", ".join(sorted(members)[:8])
        failures.append(
            f"[scc] 包级连通团增大：{size} > 上界 {limit}（新增环）。示例成员：{sample} ...")
    elif size < limit:
        notes.append(f"[scc] 包级连通团缩小：{size} < 上界 {limit}，请收缩 baseline")
    return failures, notes


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


def selftest_feature_whitelist() -> list:
    """feature-to-feature 白名单正向断言：common/theme 与同特征必须放行。"""
    problems = []
    pkg = "com.asmr.player.ui.library"
    cases = {
        "import com.asmr.player.ui.settings.SettingsViewModel": False,
        "import com.asmr.player.ui.common.dialog.SomeDialog": True,
        "import com.asmr.player.ui.theme.AppTheme": True,
        "import com.asmr.player.ui.library.LibraryViewModel": True,
    }
    for imp, should_pass in cases.items():
        caught = match_imports("selftest", pkg, [imp])
        if should_pass and caught:
            problems.append(f"[selftest] feature-to-feature 白名单误伤 {imp}")
        if not should_pass and not caught:
            problems.append(f"[selftest] feature-to-feature 白名单漏放 {imp}")
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


def selftest_scc() -> list:
    """SCC 判定逻辑自检（纯合成图，证明环可识别、无环不误报）。"""
    problems = []
    cyc = {"a": {"b"}, "b": {"a"}, "c": {"a"}}
    if len(biggest_scc(cyc)) != 2:
        problems.append("[selftest] scc: 合成 2-环未被识别")
    acyclic = {"a": {"b"}, "b": {"c"}, "c": set()}
    if len(biggest_scc(acyclic)) != 1:
        problems.append("[selftest] scc: 无环图误判为环")
    return problems


def main() -> int:
    failures = []

    # --- 规则自检（防空转）---
    failures.extend(selftest())
    failures.extend(selftest_feature_whitelist())
    failures.extend(selftest_size())
    failures.extend(selftest_scc())

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
    violations = collect_violations(SRC)
    failures_, baseline_hits = check_dir(violations, import_baseline)
    failures.extend(failures_)
    for h in baseline_hits:
        print(h)

    # --- baseline 失效条目检测（移包/拆文件后旧条目须同步移除）---
    failures.extend(check_dead_entries(import_baseline, violations))

    # --- 包级 SCC ratchet（断环只许减不许增）---
    scc_failures, scc_notes = check_scc(SRC, read_scc_baseline())
    failures.extend(scc_failures)
    for n in scc_notes:
        print(n)

    if failures:
        print("\n=== 架构守护失败 ===")
        for f_ in failures:
            print(f_)
        return 1
    print("架构守护通过（含规则自检）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
