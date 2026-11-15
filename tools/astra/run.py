"""Run GPT-6 Astra through the Codex CLI (AGENTS.md loop steps 2 and 4) and save its output with a header.

Adapted from SilphCo Lambda 2.0's tools/astra/run.py. It stages a read-only copy, fills the prompt's STAGE_ROOT
placeholder, runs `codex exec` read-only with the role prompt as developer instructions, keeps the raw events, and
writes docs/spec/reviews/ASTRA_<KIND>_<topic>_<date>.md verbatim under a header.

    python tools/astra/run.py --kind requirements --topic s1_pc_feasibility \
        --title "Slice S1: PC feasibility of H.264 mirroring" --prompt tools/astra/prompts/S1_REQUIREMENTS.md \
        --include ../../docs/airplay-receiver-feasibility.md

--include adds files (relative to this project) under evidence/ in the stage. --dry-run stages and prints the command.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ROLES = Path(__file__).resolve().with_name("roles")
REVIEWS = ROOT / "docs" / "spec" / "reviews"
WORK = ROOT / ".astra"
MODEL, EFFORT = "gpt-6-astra", "medium"
FLAGS = ["--json", "-m", MODEL, "-c", f'model_reasoning_effort="{EFFORT}"', "-s", "read-only",
         "-c", 'approval_policy="never"', "-c", 'windows.sandbox="elevated"', "--ignore-user-config",
         "--skip-git-repo-check"]
KINDS = {
    "requirements": dict(role="astra-requirements-author", prefix="ASTRA_REQUIREMENTS", who="Author",
                         line="Drafted specification, not an owner decision."),
    "review": dict(role="astra-scenario-reviewer", prefix="ASTRA_REVIEW", who="Reviewer",
                   line="External critique, not an owner decision."),
}
STAGE_DIRS = ("docs", "specs", "tests", "tools", "package")
SKIP = shutil.ignore_patterns("__pycache__", "*.pyc", ".venv", ".astra", "build", "runs")


def codex_exe() -> str:
    found = shutil.which("codex")
    local = Path(os.environ.get("LOCALAPPDATA", "")) / "Programs" / "OpenAI" / "Codex" / "bin" / "codex.exe"
    return found or str(local)


def stage(topic: str, includes: list[str]) -> Path:
    dest = WORK / f"stage-{topic}"
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True)
    for name in STAGE_DIRS:
        if (ROOT / name).exists():
            shutil.copytree(ROOT / name, dest / name, ignore=SKIP)
    for name in ("AGENTS.md", "README.md", "pytest.ini", "requirements-dev.txt"):
        if (ROOT / name).exists():
            shutil.copy2(ROOT / name, dest / name)
    for inc in includes:
        src = (ROOT / inc).resolve()
        (dest / "evidence").mkdir(exist_ok=True)
        shutil.copy2(src, dest / "evidence" / src.name)
    print(sum(1 for p in dest.rglob("*") if p.is_file()), "files staged at", dest)
    return dest


def fold(events: str, last: str, rc: int) -> dict:
    usage = dict(input_tokens=0, cached_input_tokens=0, output_tokens=0, reasoning_output_tokens=0)
    thread, error, turns = None, None, 0
    for line in events.splitlines():
        try:
            ev = json.loads(line)
        except ValueError:
            continue
        kind = ev.get("type")
        if kind == "thread.started":
            thread = ev.get("thread_id")
        elif kind == "turn.completed":
            turns += 1
            for k in usage:
                usage[k] += int((ev.get("usage") or {}).get(k) or 0)
        elif kind in ("turn.failed", "error"):
            error = json.dumps(ev)[:500]
    ok = rc == 0 and error is None and bool(last.strip())
    return dict(ok=ok, thread=thread, usage=usage, turns=turns, error=error)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--kind", choices=KINDS, required=True)
    ap.add_argument("--topic", required=True)
    ap.add_argument("--title", required=True)
    ap.add_argument("--prompt", required=True)
    ap.add_argument("--include", action="append", default=[])
    ap.add_argument("--timeout", type=int, default=3600)
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    k = KINDS[a.kind]
    stg = stage(a.topic, a.include)
    role = (ROLES / f"{k['role']}.md").read_text(encoding="utf-8")
    prompt = (ROOT / a.prompt).read_text(encoding="utf-8").replace("STAGE_ROOT", str(stg))
    raw = WORK / f"raw-{a.kind}-{a.topic}"
    raw.mkdir(parents=True, exist_ok=True)
    last = raw / "last_message.md"
    cmd = [codex_exe(), "exec", *FLAGS, "-o", str(last), "-C", str(stg),
           "-c", f"developer_instructions={json.dumps(role)}", "-"]
    print("codex", " ".join(FLAGS), "-o", last, "-C", stg, "-c developer_instructions=<role prompt> -")
    if a.dry_run:
        return 0
    start = time.monotonic()
    with open(raw / "events.jsonl", "w", encoding="utf-8") as out, open(raw / "stderr.txt", "w", encoding="utf-8") as err:
        try:
            rc = subprocess.run(cmd, input=prompt, text=True, encoding="utf-8", stdout=out, stderr=err,
                                timeout=a.timeout).returncode
        except subprocess.TimeoutExpired:
            print(f"codex timed out after {a.timeout} s; raw output in {raw}", file=sys.stderr)
            return 3
    secs = int(time.monotonic() - start)
    body = last.read_text(encoding="utf-8") if last.exists() else ""
    r = fold((raw / "events.jsonl").read_text(encoding="utf-8", errors="replace"), body, rc)
    if not r["ok"]:
        print(f"codex exit {rc}; {r['error'] or 'empty answer'}; raw output in {raw}", file=sys.stderr)
        return 3
    today = dt.date.today().isoformat()
    REVIEWS.mkdir(parents=True, exist_ok=True)
    dest = REVIEWS / f"{k['prefix']}_{a.topic}_{today}.md"
    u = r["usage"]
    header = (f"# {a.title}\n\n"
              f"> {k['who']}: `{MODEL}` via Codex CLI (`codex exec` read-only sandbox, role prompt "
              f"`tools/astra/roles/{k['role']}.md` as developer instructions), reasoning effort {EFFORT}.\n"
              f"> Staged: {', '.join(STAGE_DIRS)}, AGENTS.md"
              f"{' + evidence: ' + ', '.join(Path(i).name for i in a.include) if a.include else ''}.\n"
              f"> Session `{r['thread']}`, {r['turns']} turn(s), {secs} s; tokens in {u['input_tokens']} "
              f"(cached {u['cached_input_tokens']}), out {u['output_tokens']} (reasoning {u['reasoning_output_tokens']}).\n"
              f"> Prompt: `{a.prompt}`. Date {today}.\n\n**{k['line']}**\n\n---\n\n")
    dest.write_text(header + body, encoding="utf-8", newline="\n")
    print("wrote", dest.relative_to(ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
