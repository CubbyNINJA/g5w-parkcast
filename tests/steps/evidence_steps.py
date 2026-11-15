"""Steps for 06_evidence.feature's offline scenarios (AIR-012 to AIR-014)."""

from __future__ import annotations

import re

from pytest_bdd import given, parsers, then, when

from steps.fakes import s1
from steps.world import BANNER, PROJECT, SESSION, read_json, run, run_dir

Q = r'"(?P<{}>[^"]*)"'
DUMPS = ("-vdmp", "-admp", "-vrtp", "-artp")


def finished_run(w, lines=None):
    """A whole fake run whose UxPlay logs `lines` (a GO session by default), analysed and judged."""
    w.owner.end_at = 60
    w.launcher.lines = [(1, BANNER)] + [(10 + i, line) for i, line in enumerate(lines or SESSION["GO"])]
    run(w)
    w.log = run_dir(w) / "uxplay.log"
    w.report = s1("analyser").analyse(w.log, command=w.record.command)
    w.judgement = s1("evaluator").judge(w.report, notes={}, ended_at=w.record.ended_at, end_reason=w.record.reason)


def write_evidence(w):
    if getattr(w, "evidence", None) is None:
        w.evidence = s1("evidence").write(run_dir(w), record=w.record.to_dict(), report=w.report,
                                          judgement=w.judgement, owner=w.owner, ios=w.ios)
    return w.evidence


@given("a finished run with a GO verdict")
@given("a finished run whose log shows no iOS version")
def _(world):
    finished_run(world)


@given(parsers.re("a synthetic run record with iOS version " + Q.format("version") + " and no build number"))
def _(world, version):
    finished_run(world)
    world.ios = {"version": version, "build": None}


@given("a synthetic run record with no iOS version or build")
def _(world):
    finished_run(world)


@given("the owner gives no answer")
def _(world):
    world.owner.answers = []


@when("the evidence is written")
def _(world):
    write_evidence(world)


@when(parsers.re("the owner answers only " + Q.format("answer")))
def _(world, answer):
    world.owner.answers = [answer]
    write_evidence(world)


@then(parsers.re('the run\'s folder under "runs/" holds the log, the analyser report, every session verdict and the run verdict'))
def _(world):
    rd = run_dir(world)
    assert rd.parent.name == "runs"
    assert (rd / "uxplay.log").is_file()
    assert read_json(rd / "report.json")["sessions"]
    verdict = read_json(rd / "verdict.json")
    assert verdict["verdict"] == world.judgement.verdict
    assert [s["verdict"] for s in verdict["sessions"]] == [s.verdict for s in world.judgement.sessions]


@then(parsers.re(Q.format("folder") + " is excluded from version control"))
def _(world, folder):
    ignored = (PROJECT / ".gitignore").read_text(encoding="utf-8").splitlines()
    assert folder in ignored


@then("the owner is asked for the full iOS version with its build number, from Settings > General > About")
def _(world):
    asked = " ".join(world.owner.asked)
    assert "Settings > General > About" in asked and "build" in asked.lower()


@then(parsers.re("the answer " + Q.format("answer") + " is recorded(?: exactly,)? as " + Q.format("provenance")))
def _(world, answer, provenance):
    ev = write_evidence(world)
    assert (ev.ios, ev.ios_provenance) == (answer, provenance)
    saved = read_json(run_dir(world) / "run.json")["ios"]
    assert (saved["value"], saved["provenance"]) == (answer, provenance)


@then("the build number is recorded as missing")
def _(world):
    assert "ios build" in write_evidence(world).missing


@then("the iOS version is recorded as missing")
def _(world):
    assert "ios version" in write_evidence(world).missing


@then("the evidence is marked incomplete")
def _(world):
    assert write_evidence(world).incomplete is True
    assert read_json(run_dir(world) / "run.json")["evidence_complete"] is False


@given(parsers.re("a finished run whose log names " + Q.format("name") + " with deviceID " + Q.format("device")
                  + " from address " + Q.format("address")))
def _(world, name, device, address):
    lines = [l.replace("Fixture iPhone", name).replace("AA:BB:CC:DD:EE:FF", device) for l in SESSION["GO"]]
    finished_run(world, [f"accepted connection from {address}", *lines])


@when(parsers.re("the redacted summary is written to " + Q.format("dest")))
def _(world, dest):
    write_evidence(world)
    world.summary = s1("evidence").write_summary(world.tmp / dest, run_dir(world), tier="pc").read_text(encoding="utf-8")


@then(parsers.re("the summary contains the verdict, the model " + Q.format("model") + " and the iOS version"))
def _(world, model):
    for text in (world.judgement.verdict, model, world.evidence.ios):
        assert text in world.summary, text


@then(parsers.re("the summary contains neither " + Q.format("a") + ", " + Q.format("b") + " nor " + Q.format("c")))
def _(world, a, b, c):
    for text in (a, b, c):
        assert text not in world.summary, text


@then("the run's folder, the build tree and the runner's working directory hold no video, image or stream dump file")
def _(world):
    from pathlib import Path
    places = [run_dir(world), Path(world.provenance.build_dir), world.cwd]
    assert s1("evidence").screen_content(places) == []


@then('the recorded launch command holds none of "-vdmp", "-admp", "-vrtp" or "-artp"')
def _(world):
    command = read_json(run_dir(world) / "run.json")["command"]
    assert not set(command) & set(DUMPS)


@given(parsers.re("an? (?P<tier>offline|pc) result with a GO verdict"))
def _(world, tier):
    finished_run(world)
    write_evidence(world)
    world.tier = tier


@when("the summary is written")
def _(world):
    world.summary = s1("evidence").write_summary(world.tmp / "docs" / "evidence", run_dir(world),
                                                 tier=world.tier).read_text(encoding="utf-8")


@then(parsers.re("the summary names the tier " + Q.format("tier")))
def _(world, tier):
    assert re.search(rf"\btier\b\W+{re.escape(tier)}\b", world.summary, re.I), world.summary


@then("the summary says the head unit is untested")
def _(world):
    assert re.search(r"head unit\W+(?:is\W+)?untested", world.summary, re.I), world.summary


@then("the summary does not claim the owner's iPhone mirrors to the receiver")
def _(world):
    text = world.summary.lower()
    assert "not evidence" in text
    assert "owner's iphone" not in text
