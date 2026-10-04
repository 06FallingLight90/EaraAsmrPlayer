#!/usr/bin/env python3
"""机械重写架构守护 baseline（仅用于**行为保持**的移包/拆文件之后同步重键）。

背景：`import-direction-baseline.txt` 以 "<文件相对路径> <import FQ>" 为键；
移包后旧条目会变 dead（由 ci_guard 的失效条目检测报出）、新 FQ 不在 baseline 即失败。
本脚本从**实况**重算并覆盖 baseline，供迁移后一次性重键。

用法（仓库根目录运行）：
    python tools/refresh_baseline.py import   # 重写 import-direction-baseline.txt
    python tools/refresh_baseline.py size     # 重写 size-guard-baseline.txt
    python tools/refresh_baseline.py scc      # 重写 package-scc-baseline.txt 的 max_scc_size

纪律：本脚本会把**当前所有违规**接受为新 baseline。运行后必须 `git diff` 人工复核，
确认新增条目全部是本次迁移的连带项；不得用它"顺手放过"新引入的架构违规。
"""
import sys

import ci_guard as g


def read_entries(path) -> list:
    if not path.exists():
        return []
    return [ln.strip() for ln in path.read_text(encoding="utf-8").splitlines() if ln.strip()]


def rewrite_import() -> None:
    new = {f"{rel} {fq}" for _rule, rel, _no, fq in g.collect_violations(g.SRC)}
    old = read_entries(g.IMPORT_BASELINE)
    kept = [e for e in old if e in new]
    added = sorted(new - set(old))
    g.IMPORT_BASELINE.write_text("\n".join(kept + added) + "\n", encoding="utf-8")
    print(f"[import] 已重写 {g.IMPORT_BASELINE}（保留 {len(kept)}，移除 {len(old) - len(kept)}，新增 {len(added)}）")


def rewrite_size() -> None:
    current = {}
    for f in sorted(g.SRC.rglob("*.kt")):
        rel = f.relative_to(g.REPO).as_posix()
        line_count = len(g.read_lines(f))
        if line_count > g.SIZE_LIMIT:
            current[rel] = line_count
    old = read_entries(g.SIZE_BASELINE)
    rows = []
    for entry in old:
        rel = entry.rpartition(":")[0].strip()
        if rel in current:
            rows.append(f"{rel}: {current.pop(rel)}")
    rows.extend(f"{rel}: {cap}" for rel, cap in sorted(current.items()))
    g.SIZE_BASELINE.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"[size] 已重写 {g.SIZE_BASELINE}（{len(rows)} 条）")


def rewrite_scc() -> None:
    size = len(g.biggest_scc(g.build_package_graph(g.SRC)))
    text = g.SCC_BASELINE.read_text(encoding="utf-8") if g.SCC_BASELINE.exists() else ""
    lines, replaced = [], False
    for line in text.splitlines():
        if line.strip().startswith("max_scc_size"):
            lines.append(f"max_scc_size={size}")
            replaced = True
        else:
            lines.append(line)
    if not replaced:
        lines.append(f"max_scc_size={size}")
    g.SCC_BASELINE.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"[scc] 已重写 {g.SCC_BASELINE}（max_scc_size={size}）")


def main() -> int:
    actions = {"import": rewrite_import, "size": rewrite_size, "scc": rewrite_scc}
    if len(sys.argv) != 2 or sys.argv[1] not in actions:
        print(__doc__)
        return 2
    actions[sys.argv[1]]()
    return 0


if __name__ == "__main__":
    sys.exit(main())
