"""The test's one temporary inbound rule for the built uxplay.exe (AIR-003 to AIR-005, AIR-030;
OD-05, OD-27). The runner owns a rule only if it has the runner's name and its program is a
uxplay.exe under the recorded build root; it never touches any other rule."""

from __future__ import annotations

import dataclasses
import json
import subprocess
from pathlib import Path, PureWindowsPath

RULE_NAME = "NEMESIS-AirPlay-S1"


class FirewallError(Exception):
    pass


@dataclasses.dataclass(frozen=True)
class Rule:
    name: str
    program: str
    direction: str = "in"
    action: str = "allow"
    profiles: tuple = ("Public",)
    ports: object = None
    id: str | None = None

    def __post_init__(self):
        object.__setattr__(self, "profiles", tuple(self.profiles))


def _norm(path: str) -> str:
    return str(PureWindowsPath(path)).lower().rstrip("\\")


def is_owned(rule: Rule, build_root) -> bool:
    if rule.name != RULE_NAME:
        return False
    program, root = _norm(rule.program), _norm(str(build_root))
    return PureWindowsPath(program).name == "uxplay.exe" and program.startswith(root + "\\")


def rule_dict(rule: Rule) -> dict:
    d = dataclasses.asdict(rule)
    d["profiles"] = list(rule.profiles)
    return d


class WindowsFirewall:
    """Windows Firewall through PowerShell's NetSecurity cmdlets. Needs an elevated terminal."""

    def _ps(self, script: str, timeout: int = 600) -> str:
        res = subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-Command",
                              "$ErrorActionPreference='Stop'; [Console]::OutputEncoding=[Text.Encoding]::UTF8; "
                              + script], capture_output=True, encoding="utf-8-sig", timeout=timeout)
        if res.returncode != 0:
            raise FirewallError(res.stderr.strip() or f"PowerShell exit {res.returncode}")
        return res.stdout

    def active_profile(self) -> str:
        out = self._ps("(Get-NetConnectionProfile | Select-Object -First 1).NetworkCategory")
        return out.strip()

    def list_rules(self) -> list[Rule]:
        out = self._ps(
            "$apps=@{}; Get-NetFirewallApplicationFilter -All | ForEach-Object { $apps[$_.InstanceID]=$_.Program }; "
            "$ports=@{}; Get-NetFirewallPortFilter -All | ForEach-Object { $ports[$_.InstanceID]=\"$($_.LocalPort)\" }; "
            "Get-NetFirewallRule | ForEach-Object { [pscustomobject]@{ Id=$_.Name; Name=$_.DisplayName; "
            "Program=\"$($apps[$_.InstanceID])\"; Direction=\"$($_.Direction)\"; Action=\"$($_.Action)\"; "
            "Profile=\"$($_.Profile)\"; Ports=\"$($ports[$_.InstanceID])\" } } | ConvertTo-Json -Compress")
        rows = json.loads(out) if out.strip() else []
        rows = rows if isinstance(rows, list) else [rows]
        direction = {"Inbound": "in", "Outbound": "out"}
        return [Rule(name=r["Name"], program=r["Program"], direction=direction.get(r["Direction"], r["Direction"]),
                     action=r["Action"].lower(), profiles=tuple(p.strip() for p in r["Profile"].split(",")),
                     ports=None if r["Ports"] in ("", "Any") else r["Ports"], id=r["Id"])
                for r in rows]

    def create(self, rule: Rule) -> Rule:
        if (rule.direction, rule.action, rule.ports) != ("in", "allow", None):
            raise FirewallError("the runner only creates inbound allow rules with no port restriction")
        program = str(Path(rule.program))
        profiles = ",".join(rule.profiles)
        out = self._ps(f"(New-NetFirewallRule -DisplayName '{RULE_NAME}' -Direction Inbound -Action Allow "
                       f"-Program '{program}' -Profile {profiles}).Name")
        return dataclasses.replace(rule, id=out.strip())

    def remove(self, rule_id: str) -> None:
        self._ps(f"Remove-NetFirewallRule -Name '{rule_id}'")
