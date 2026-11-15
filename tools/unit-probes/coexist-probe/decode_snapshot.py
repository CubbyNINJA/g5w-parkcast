"""Decode a coexist-probe snapshot: listening TCP ports and bound UDP ports with their owning processes,
and counts of established connections. Remote addresses are never printed."""
import re
import sys
from pathlib import Path


def sockets(path: Path, tcp: bool):
    rows = []
    if not path.is_file():
        return rows
    for line in path.read_text(errors="replace").splitlines()[1:]:
        f = line.split()
        if len(f) < 10:
            continue
        local, remote, state, inode = f[1], f[2], f[3], f[9]
        ip_hex, port_hex = local.rsplit(":", 1)
        rows.append({"port": int(port_hex, 16), "state": state, "inode": inode,
                     "any": set(ip_hex) == {"0"}, "remote_zero": set(remote.split(":")[0]) == {"0"}})
    return rows


def owners(path: Path):
    owner, current = {}, None
    for line in path.read_text(errors="replace").splitlines():
        m = re.match(r"== pid (\d+) (.*)", line)
        if m:
            current = f"{m.group(2).replace(chr(0), ' ').strip() or '?'} (pid {m.group(1)})"
            continue
        m = re.search(r"socket:\[(\d+)\]", line)
        if m and current:
            owner.setdefault(m.group(1), current)
    return owner


for snap in sys.argv[1:]:
    d = Path(snap)
    own = owners(d / "socket-owners.txt")
    print(f"\n=== {d.parent.name}/{d.name}")
    for kind in ("tcp", "tcp6"):
        rows = sockets(d / f"proc-net-{kind}.txt", True)
        listen = sorted({(r["port"], own.get(r["inode"], "unknown owner"), r["any"]) for r in rows if r["state"] == "0A"})
        est = [r for r in rows if r["state"] == "01"]
        print(f"{kind}: {len(est)} established")
        for port, who, any_ in listen:
            print(f"  LISTEN {port:<6} {'all addresses' if any_ else 'one address'}  {who}")
        est_ports = sorted({(r['port'], own.get(r['inode'], 'unknown owner')) for r in est})
        for port, who in est_ports:
            print(f"  ESTAB  local port {port:<6} {who}")
    for kind in ("udp", "udp6"):
        rows = sockets(d / f"proc-net-{kind}.txt", False)
        bound = sorted({(r["port"], own.get(r["inode"], "unknown owner")) for r in rows})
        print(f"{kind}: " + ", ".join(f"{p} {w.split(' (')[0]}" for p, w in bound))
