"""Static check of a stick script's logcat calls (AIR-089(c), OD-202).

A fake shell on the PC can't see a shell redirection, so the rule "unfiltered logcat never lands on the stick,
even for a moment" is checked on the script's text: every `logcat` call must have no `-f` option and no
redirect or input of its own, and its output must go through a single pipe straight into `grep`. (`-d` is not
required: AIR-089(c) is about what lands on the stick, not about how long logcat runs.)

Limits: the check reads the script as written. A call made through `eval`, a variable or another script is not
seen, and the scripts it is meant for (`tools/airplay_s3/stick/*.sh`) use none of those.

    python tools/airplay_s3/stickcheck.py tools/airplay_s3/stick/parkcast-diag.sh
"""

from __future__ import annotations

import sys
from pathlib import Path

_SEPARATORS = ("||", "&&", "|", ";", "&", "\n")
_ALLOWED_REDIRECTS = ("2>&1", "2>/dev/null")


def _logical_lines(text):
    """[(first line number, text)] with backslash-newline continuations joined."""
    out = []
    start = None
    buf = ""
    for n, line in enumerate(text.replace("\r\n", "\n").split("\n"), 1):
        if start is None:
            start = n
        if line.endswith("\\") and not line.endswith("\\\\"):
            buf += line[:-1] + " "
            continue
        out.append((start, buf + line))
        start = None
        buf = ""
    return out


def _tokens(line):
    """Split one logical line into words and operators, skipping unquoted comments.

    Returns [(kind, text, start, end)]: kind is "word" or "op"; start and end index the line."""
    toks = []
    i = 0
    n = len(line)
    while i < n:
        c = line[i]
        if c in " \t":
            i += 1
            continue
        if c == "#":
            break
        if c == "\n":
            toks.append(("op", "\n", i, i + 1))
            i += 1
            continue
        op = next((s for s in _SEPARATORS if line.startswith(s, i)), None)
        if op:
            toks.append(("op", op, i, i + len(op)))
            i += len(op)
            continue
        if c in "<>" or (c.isdigit() and i + 1 < n and line[i + 1] in "<>"):
            j = i + 1
            while j < n and line[j] in "<>&":
                j += 1
            k = j
            while k < n and line[k] in " \t":
                k += 1
            end = _word_end(line, k) if k < n and line[k] not in "|;&\n" else k
            toks.append(("redir", line[i:end].replace(" ", "").replace("\t", ""), i, end))
            i = end
            continue
        if c in "(){}":
            toks.append(("op", c, i, i + 1))
            i += 1
            continue
        end = _word_end(line, i)
        toks.append(("word", line[i:end], i, end))
        i = end
    return toks


def _word_end(line, i):
    n = len(line)
    quote = None
    depth = 0
    while i < n:
        c = line[i]
        if quote:
            if c == "\\" and quote == '"':
                i += 2
                continue
            if c == quote:
                quote = None
            i += 1
            continue
        if c == "\\":
            i += 2
            continue
        if c in "'\"":
            quote = c
            i += 1
            continue
        if line.startswith("$(", i):
            depth += 1
            i += 2
            continue
        if c == ")" and depth:
            depth -= 1
            i += 1
            continue
        if depth == 0 and (c in " \t\n;|&<>(){}"):
            break
        i += 1
    return i


def _is_logcat(word):
    w = word.strip("'\"")
    return w == "logcat" or w.endswith("/logcat")


def logcat_calls(text):
    """[(line number, call text, [problems])] for every logcat call in a shell script's text."""
    calls = []
    for lineno, line in _logical_lines(text):
        toks = _tokens(line)
        command_position = True
        for idx, (kind, value, start, _) in enumerate(toks):
            if kind == "op":
                command_position = value not in (")", "}")
                continue
            if kind == "redir":
                continue
            if command_position and _is_logcat(value):
                calls.append(_judge(line, lineno, toks, idx))
            if command_position and value in ("if", "then", "else", "elif", "do", "while", "until", "!", "time"):
                continue
            command_position = False
    return calls


def _judge(line, lineno, toks, idx):
    problems = []
    end_idx = idx + 1
    while end_idx < len(toks) and not (toks[end_idx][0] == "op" and toks[end_idx][1] in _SEPARATORS + (")", "}")):
        end_idx += 1
    segment = toks[idx:end_idx]
    call_text = line[toks[idx][2]:segment[-1][3]].strip()
    words = [t[1] for t in segment[1:] if t[0] == "word"]
    redirs = [t[1] for t in segment if t[0] == "redir"]
    for w in words:
        bare = w.strip("'\"")
        if bare.startswith("-f") or bare.startswith("--file"):
            problems.append(f"writes a file itself ({bare})")
    for r in redirs:
        if r not in _ALLOWED_REDIRECTS:
            problems.append(f"has a redirect before any pipe ({r})")
    after = toks[end_idx] if end_idx < len(toks) else None
    if after is None or after[0] != "op" or after[1] != "|":
        problems.append("its output does not go through a pipe")
    else:
        nxt = toks[end_idx + 1] if end_idx + 1 < len(toks) else None
        if nxt is None or nxt[0] != "word" or nxt[1].strip("'\"").rsplit("/", 1)[-1] != "grep":
            problems.append("its pipe does not go straight into grep")
    return (lineno, call_text, problems)


def logcat_problems(text, name="script"):
    """Every problem as '<name>: line N: <call text>: <reason>'; [] when every call is safe."""
    out = []
    for lineno, call_text, problems in logcat_calls(text):
        for p in problems:
            out.append(f"{name}: line {lineno}: {call_text}: {p}")
    return out


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if not argv:
        print("usage: stickcheck.py SCRIPT...", file=sys.stderr)
        return 2
    bad = 0
    for arg in argv:
        path = Path(arg)
        text = path.read_text(encoding="utf-8")
        calls = logcat_calls(text)
        problems = logcat_problems(text, arg)
        for p in problems:
            print(p)
        if problems:
            bad = 1
        else:
            print(f"{arg}: {len(calls)} logcat call(s), each piped straight into grep")
    return bad


if __name__ == "__main__":
    sys.exit(main())
