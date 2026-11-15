"""Steps for 01_build.feature (AIR-001, AIR-002)."""

from __future__ import annotations

import hashlib
import json
import time
from pathlib import Path

import pytest
from pytest_bdd import given, parsers, then, when

from steps.fakes import s1
from steps.world import PROJECT, REPO

Q = r'"(?P<{}>[^"]*)"'
UCRT64 = "the UCRT64 environment"
DECODER = "a GStreamer H.264 decoder"


def _inventory(packages: bool = True, decoder: bool = True):
    prereqs = s1("prereqs")
    return prereqs.Inventory(ucrt64=True, packages={p: f"1.0-{i}" for i, p in enumerate(prereqs.REQUIRED_PACKAGES)},
                             gst_elements={"avdec_h264"} if decoder else set())


def _build(w, pinned=None):
    w.used_inventory = getattr(w, "inventory", None) or _inventory()
    w.build_result = s1("build").build(inventory=w.used_inventory, upstream=w.upstream, compiler=w.compiler,
                                       work_root=w.work_root, owner=w.owner, pinned=pinned)
    return w.build_result


def _not_selectable(w):
    build = s1("build")
    with pytest.raises(build.NotVerified):
        build.load_verified(w.work_root)


@given("a prerequisite inventory with every required MSYS2 UCRT64 tool and package")
def _(world):
    world.inventory = _inventory(decoder=False)


@given("the inventory lists a GStreamer H.264 decoder")
def _(world):
    world.inventory.gst_elements.add("avdec_h264")


@given(parsers.re(Q.format("item") + " is missing from the inventory"))
def _(world, item):
    inv = world.inventory
    if item == UCRT64:
        inv.ucrt64 = False
    elif item == DECODER:
        inv.gst_elements.clear()
    else:
        assert item in s1("prereqs").REQUIRED_PACKAGES, f"{item} isn't a required package"
        del inv.packages[item]


@when("the prerequisite check runs")
def _(world):
    _build(world)


@then("the check passes")
def _(world):
    assert world.build_result.check.ok
    assert world.build_result.check.missing == []


@then("the build may start")
def _(world):
    assert world.events.of("build.compile")


@then(parsers.re("the check fails naming " + Q.format("item")))
def _(world, item):
    check = world.build_result.check
    assert not check.ok
    assert check.missing == [item]
    assert world.build_result.status == "blocked"


@then("the build is never invoked")
def _(world):
    assert not world.events.of("build.checkout", "build.compile")


@given(parsers.re("upstream master is at commit " + Q.format("commit") + " with version " + Q.format("version")))
def _(world, commit, version):
    world.upstream.master = commit
    world.upstream.versions[commit] = version


@when("the build procedure runs and the build succeeds")
def _(world):
    world.compiler.mode = "ok"
    _build(world)


@when("the build procedure runs and the build fails")
def _(world):
    world.compiler.mode = "fail"
    _build(world)


@when("the build procedure runs")
def _(world):
    _build(world)


@then(parsers.re("the provenance records commit " + Q.format("commit")))
def _(world, commit):
    assert world.build_result.status == "built"
    assert world.build_result.provenance.commit == commit
    assert s1("build").load_provenance(world.work_root).commit == commit


@then("the provenance records the dependency versions, the build commands, the executable path and its SHA-256")
def _(world):
    compiled = world.events.of("build.compile")[-1]
    exe = Path(compiled["build"]) / "uxplay.exe"
    expected = dict(commit=world.upstream.master, version="1.74", dependencies=world.used_inventory.packages,
                    commands=world.compiler.commands, executable=str(exe),
                    sha256=hashlib.sha256(exe.read_bytes()).hexdigest(), build_dir=compiled["build"],
                    source_dir=compiled["source"], build_root=str(world.work_root))
    for prov in (world.build_result.provenance, s1("build").load_provenance(world.work_root)):
        for field, value in expected.items():
            assert getattr(prov, field) == value, field


@then("the source and build trees are outside version control")
def _(world):
    prov = world.build_result.provenance
    for d in (prov.source_dir, prov.build_dir, s1("build").DEFAULT_WORK_ROOT):
        assert not Path(d).resolve().is_relative_to(REPO.resolve()), d


@given(parsers.re("a recorded build of commit " + Q.format("commit")))
def _(world, commit):
    world.upstream.master = commit
    world.upstream.versions[commit] = "1.74"
    assert _build(world).status == "built"
    world.events.clear()


@given(parsers.re("upstream master has since moved to commit " + Q.format("commit")))
def _(world, commit):
    world.upstream.master = commit
    world.upstream.versions[commit] = "1.74"


@when("the build procedure is repeated from its recorded provenance")
def _(world):
    _build(world, pinned=s1("build").load_provenance(world.work_root))


@then(parsers.re("it builds commit " + Q.format("commit")))
def _(world, commit):
    assert [e["commit"] for e in world.events.of("build.checkout")] == [commit]
    assert world.build_result.provenance.commit == commit


@given("an executable left by an earlier build in the build tree")
def _(world):
    assert _build(world).status == "built"
    world.earlier_exe = Path(world.build_result.provenance.executable)
    world.earlier_bytes = world.earlier_exe.read_bytes()
    world.events.clear()


@given("the compiler will fail before changing any output")
def _(world):
    world.compiler.mode = "fail-early"


@then("the failure is reported")
def _(world):
    result = world.build_result
    assert result.status in ("failed", "interrupted")
    assert result.error
    if world.compiler.mode == "interrupt":
        assert result.status == "interrupted" and "interrupt" in result.error.lower()


@then("the earlier executable's bytes are unchanged")
def _(world):
    assert world.earlier_exe.read_bytes() == world.earlier_bytes


@then("no executable is recorded as this build's output")
def _(world):
    assert world.build_result.provenance is None
    _not_selectable(world)


@then("the earlier executable is not selected for a test run")
def _(world):
    _not_selectable(world)


@given("a compiler that writes a partial executable before it stops")
def _(world):
    world.compiler.mode = "fail"


@when(parsers.re("the build ends by (?P<ending>a compiler failure|a keyboard interrupt)"))
def _(world, ending):
    world.compiler.mode = "fail" if ending == "a compiler failure" else "interrupt"
    _build(world)


@then("the partial executable is not recorded as a successful build output")
def _(world):
    assert world.build_result.provenance is None
    assert not (world.work_root / "provenance.json").exists()


@then("it can't be selected for a test run")
def _(world):
    _not_selectable(world)


@then("it stops before building")
def _(world):
    assert world.build_result.status == "stopped"
    assert not world.events.of("build.checkout", "build.compile")


@then(parsers.re("it asks the owner which version to build, naming version " + Q.format("version")))
def _(world, version):
    assert len(world.owner.asked) == 1 and version in world.owner.asked[0]


# --- @host: the real build on this PC -------------------------------------------------------

def _fresh_dir(label: str) -> Path:
    d = Path(s1("build").DEFAULT_WORK_ROOT) / time.strftime(f"{label}-%Y%m%d-%H%M%S")
    assert not d.exists(), f"{d} already exists, so it isn't a clean directory"
    return d


@given("this PC's MSYS2 UCRT64 environment")
def _(world):
    world.host_build = s1("build").HostBuild()


@when("the prerequisite check runs and its inventory is saved")
def _(world):
    world.inventory_dir = _fresh_dir("inventory")
    world.host_check = world.host_build.check_and_save(world.inventory_dir)


@when("the build procedure runs from a clean build directory")
def _(world):
    world.dest_first = _fresh_dir("build-a")
    world.host_first = world.host_build.build_clean(world.dest_first)


@when("it is repeated from a second clean build directory at the recorded commit")
def _(world):
    time.sleep(1)  # the directory names carry the second they were made
    world.dest_second = _fresh_dir("build-b")
    world.host_second = world.host_build.build_clean(world.dest_second, pinned=world.host_first.provenance)


@then(parsers.re("both builds report UxPlay version " + Q.format("version") + " at the same commit"))
def _(world, version):
    assert world.host_check.ok, world.host_check.missing
    saved = json.loads((world.inventory_dir / "inventory.json").read_text(encoding="utf-8"))
    assert saved["ucrt64"] is True and "avdec_h264" in saved["gst_elements"]
    assert set(s1("prereqs").REQUIRED_PACKAGES) <= set(saved["packages"])
    a, b = world.host_first.provenance, world.host_second.provenance
    assert a.version == b.version == version
    assert a.commit == b.commit


@then("each build's provenance is saved with its commit, dependency versions, build commands, executable path and SHA-256")
def _(world):
    assert world.dest_first != world.dest_second
    fields = ("commit", "version", "build_root", "source_dir", "build_dir", "executable", "sha256", "dependencies",
              "commands")
    for result, dest in ((world.host_first, world.dest_first), (world.host_second, world.dest_second)):
        returned = result.provenance
        assert Path(returned.build_root).resolve() == dest.resolve(), "the build didn't use the requested directory"
        for d in (returned.source_dir, returned.build_dir, returned.executable):
            assert Path(d).resolve().is_relative_to(dest.resolve()), d
        saved = s1("build").load_provenance(dest)
        for field in fields:
            assert getattr(saved, field) == getattr(returned, field), field
        for field in ("commit", "dependencies", "commands"):
            assert getattr(saved, field), field
        inventory = json.loads((world.inventory_dir / "inventory.json").read_text(encoding="utf-8"))
        for package in s1("prereqs").REQUIRED_PACKAGES:
            assert saved.dependencies.get(package) == inventory["packages"][package], package
        assert saved.sha256 == hashlib.sha256(Path(saved.executable).read_bytes()).hexdigest()
    assert world.host_second.provenance.commit == world.host_first.provenance.commit


@then("neither build tree is inside version control")
def _(world):
    for result in (world.host_first, world.host_second):
        for d in (result.provenance.build_dir, result.provenance.source_dir):
            assert not Path(d).resolve().is_relative_to(REPO.resolve())
            assert not Path(d).resolve().is_relative_to(PROJECT.resolve())
