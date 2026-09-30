#!/usr/bin/env python3
"""架构守护（S15）：
1. 单文件行数上限：>1500 行禁入（ratchet——存量超限文件记录在
   tools/size-guard-baseline.txt，新增超限即失败；存量修复后须同步收缩
   baseline，防止回潮）。
2. import 方向：data 层禁止引用 playback/ui/main（分层穿透）。
   存量违规记录在 tools/import-direction-baseline.txt，同样 ratchet。

用法：python3 tools/ci_guard.py（仓库根目录运行）
"""

import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "app" / "src" / "main" / "java"
SIZE_LIMIT = 1500
SIZE_BASELINE = Path(__file__).resolve().parent / "size-guard-baseline.txt"
IMPORT_BASELINE = Path(__file__).resolve().parent / "import-direction-baseline.txt"

FORBIDDEN_IN_DATA = ("com.asmr.player.playback.", "com.asmr.player.ui.", "com.asmr.player.main.")


def read_baseline(path: Path) -> set[str]:
    if not path.exists():
        return set()
    return {line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()}


def main() -> int:
    failures: list[str] = []
    kt_files = sorted(SRC.rglob("*.kt"))

    # --- 行数守护 ---
    size_baseline = read_baseline(SIZE_BASELINE)
    current_oversized = set()
    for f in kt_files:
        rel = f.relative_to(REPO).as_posix()
        line_count = sum(1 for _ in f.open(encoding="utf-8", errors="replace"))
        if line_count > SIZE_LIMIT:
            current_oversized.add(rel)
            if rel not in size_baseline:
                failures.append(f"[size] 新增超限文件 {rel}（{line_count} 行 > {SIZE_LIMIT}），禁止入库")
    for rel in sorted(size_baseline - current_oversized):
        print(f"[size] 存量超限文件已修复，请从 baseline 移除：{rel}")

    # --- import 方向守护 ---
    import_baseline = read_baseline(IMPORT_BASELINE)
    for f in kt_files:
        rel = f.relative_to(REPO).as_posix()
        if "/data/" not in f"/{rel}":
            continue
        for i, line in enumerate(f.open(encoding="utf-8", errors="replace"), start=1):
            stripped = line.strip()
            if not stripped.startswith("import "):
                continue
            target = stripped[len("import "):].rstrip()
            if any(target.startswith(prefix) for prefix in FORBIDDEN_IN_DATA):
                entry = f"{rel}:{i} {target}"
                if entry not in import_baseline and f"{rel} {target}" not in import_baseline:
                    failures.append(f"[import] data 层违规引用 {entry}")
                else:
                    print(f"[import] 存量违规（baseline）：{rel}:{i}")

    if failures:
        print("\n=== 架构守护失败 ===")
        for f_ in failures:
            print(f_)
        return 1
    print("架构守护通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
