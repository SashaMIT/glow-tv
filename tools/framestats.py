#!/usr/bin/env python3
"""Splits frames from `dumpsys gfxinfo <pkg> framestats` into stages and says which ones make late frames late.

Usage: adb shell dumpsys gfxinfo com.tv98.glow framestats | tools/framestats.py [label]
Stages (ms): wait = vsync to the app starting the frame, input, anim, layout (measure + layout), record (onDraw),
upload (sync: textures and pictures sent to the GPU), gpu (issuing draw commands), swap.
"""
import statistics
import sys

STAGES = [("wait", "IntendedVsync", "HandleInputStart"), ("input", "HandleInputStart", "AnimationStart"),
          ("anim", "AnimationStart", "PerformTraversalsStart"), ("layout", "PerformTraversalsStart", "DrawStart"),
          ("record", "DrawStart", "SyncQueued"), ("queue", "SyncQueued", "SyncStart"),
          ("upload", "SyncStart", "IssueDrawCommandsStart"), ("gpu", "IssueDrawCommandsStart", "SwapBuffers"),
          ("swap", "SwapBuffers", "FrameCompleted")]
LATE = 16.67


def frames(text):
    head, out, inside = None, [], False
    for line in text.splitlines():
        line = line.strip().rstrip(",")
        if line == "---PROFILEDATA---":
            inside = not inside
            head = None
            continue
        if not inside or not line:
            continue
        cells = line.split(",")
        if head is None:
            head = cells
            continue
        row = dict(zip(head, (int(c) for c in cells)))
        if row.get("Flags", 0) != 0:  # skipped or first-draw frames
            continue
        out.append(row)
    return out


def main():
    label = sys.argv[1] if len(sys.argv) > 1 else ""
    rows = frames(sys.stdin.read())
    if not rows:
        print(f"{label}: no frames")
        return
    late = [r for r in rows if (r["FrameCompleted"] - r["IntendedVsync"]) / 1e6 > LATE]
    print(f"{label}: {len(late)}/{len(rows)} late ({100 * len(late) / len(rows):.1f} %)")
    for name, group in (("all", rows), ("late", late)):
        if not group:
            continue
        parts = []
        for st, a, b in STAGES:
            vals = [(r[b] - r[a]) / 1e6 for r in group]
            parts.append(f"{st} {statistics.median(vals):.1f}/{sorted(vals)[int(0.9 * (len(vals) - 1))]:.1f}")
        for col, st in (("DequeueBufferDuration", "dequeue"), ("QueueBufferDuration", "queuebuf")):
            if col in group[0]:  # inside gpu and swap: waiting for a free screen buffer, handing one over
                vals = [r[col] / 1e6 for r in group]
                parts.append(f"{st} {statistics.median(vals):.1f}/{sorted(vals)[int(0.9 * (len(vals) - 1))]:.1f}")
        total = [(r["FrameCompleted"] - r["IntendedVsync"]) / 1e6 for r in group]
        print(f"  {name:4} median/p90 ms: " + "  ".join(parts) + f"  total {statistics.median(total):.1f}")
    if late:
        worst = {}
        for r in late:
            st = max(STAGES, key=lambda s: r[s[2]] - r[s[1]])[0]
            worst[st] = worst.get(st, 0) + 1
        print("  biggest stage in late frames: " + ", ".join(f"{k} {v}" for k, v in sorted(worst.items(), key=lambda kv: -kv[1])))


if __name__ == "__main__":
    main()
