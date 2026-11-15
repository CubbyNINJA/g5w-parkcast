"""Steps for 01_build.feature (AIR-001, AIR-002)."""

from __future__ import annotations

import hashlib
from pathlib import Path

import pytest
from pytest_bdd import given, parsers, then, when

from steps.fakes import s1
from steps.world import PROJECT, REPO

Q = r'"(?P<{}>[^"]*)"'
UCRT64 = "the UCRT64 environment"
DECODER = "a GStreamer H.264 decoder"


def _build(w, pinned=None):
    w.used_inventory = getattr(w, "inventory", None) or _full_inventory(w)
    try:
        w.build_result = s1("build").build(
            inventory=w.used_inventory, upstream=w.upstream,
            compiler=w.compiler, work_root=w.work_root, owner=w.owner, pinned=pinned)
    except KeyboardInterrupt:
        w.build_result = None
        w.interrupted = True
    return w.build_result


def _full_inventory(w):
    prereqs = s1("prereqs")
    return prereqs.Inventory(ucrt64=True, packages={p: "1.0" for p in prereqs.REQUIRED_PACKAGES},
                             gst_elements={"avdec_h264"})


def _not_selectable(w):
    build = s1("build")
    with pytest.raises(build.NotVerified):
        build.load_verified(w.work_root)


@given("a prerequisite inventory with every required MSYS2 UCRT64 tool and package")
def _(world):
    prereqs = s1("prereqs")
    world.inventory = prereqs.Inventory(ucrt64=True, packages={p: "1.0" for p in prereqs.REQUIRED_PACKAGES},
                                        gst_elements=set())


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
    assert not world.build_result.check.ok
    assert item in world.build_result.check.missing


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
    prov = world.build_result.provenance
    assert prov.commit == commit
    assert s1("build").load_provenance(world.work_root).commit == commit


@then("the provenance records the dependency versions, the build commands, the executable path and its SHA-256")
def _(world):
    prov = world.build_result.provenance
    assert prov.dependencies == world.used_inventory.packages
    assert prov.commands == world.compiler.commands
    exe = Path(prov.executable)
    assert exe.is_file()
    assert prov.sha256 == hashlib.sha256(exe.read_bytes()).hexdigest()


@then("the source and build trees are outside version control")
def _(world):
    prov = world.build_result.provenance
    for d in (prov.source_dir, prov.build_dir, s1("build").DEFAULT_WORK_ROOT):
        assert not Path(d).resolve().is_relative_to(REPO.resolve()), d


@given(parsers.re("a recorded build of commit " + Q.format("commit")))
def _(world, commit):
    world.upstream.master = commit
    world.upstream.versions[commit] = "1.74"
    world.recorded = _build(world).provenance
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
    _build(world)
    assert world.build_result.provenance is not None
    world.events.clear()


@then("the failure is reported")
def _(world):
    if world.interrupted:
        return
    assert world.build_result.status == "failed"
    assert world.build_result.error


@then("no executable is recorded as this build's output")
def _(world):
    assert world.build_result.provenance is None
    _not_selectable(world)


@then("the earlier executable is not selected for a test run")
def _(world):
    _not_selectable(world)


@given("a build that has written a partial executable")
def _(world):
    world.compiler.mode = "fail"


@when(parsers.re("the build ends by (?P<ending>a compiler failure|a keyboard interrupt)"))
def _(world, ending):
    world.compiler.mode = "fail" if ending == "a compiler failure" else "interrupt"
    _build(world)


@then("the partial executable is not recorded as a successful build output")
def _(world):
    assert world.build_result is None or world.build_result.provenance is None
    assert not (world.work_root / "provenance.json").exists()


@then("it can't be selected for a test run")
def _(world):
    _not_selectable(world)


@then("it stops before building")
def _(world):
    assert world.build_result.status == "stopped"
    assert not world.events.of("build.compile")


@then(parsers.re("it asks the owner which version to build, naming version " + Q.format("version")))
def _(world, version):
    assert any(version in q for q in world.owner.asked)


# --- @host: the real build on this PC -------------------------------------------------------

@given("this PC's MSYS2 UCRT64 environment")
def _(world):
    world.host_build = s1("build").HostBuild()


@when("the prerequisite check runs and its inventory is saved")
def _(world):
    world.host_check = world.host_build.check_and_save()


@when("the build procedure runs from a clean build directory")
def _(world):
    world.host_first = world.host_build.build_clean()


@when("it is repeated from a second clean build directory at the recorded commit")
def _(world):
    world.host_second = world.host_build.build_clean(pinned=world.host_first.provenance)


@then(parsers.re("both builds report UxPlay version " + Q.format("version") + " at the same commit"))
def _(world, version):
    a, b = world.host_first.provenance, world.host_second.provenance
    assert a.version == b.version == version
    assert a.commit == b.commit


@then("each build's provenance is saved with its commit, dependency versions, build commands, executable path and SHA-256")
def _(world):
    for result in (world.host_first, world.host_second):
        saved = s1("build").load_provenance(Path(result.provenance.build_root))
        for field in ("commit", "dependencies", "commands", "executable", "sha256"):
            assert getattr(saved, field), field
        assert saved.sha256 == hashlib.sha256(Path(saved.executable).read_bytes()).hexdigest()


@then("neither build tree is inside version control")
def _(world):
    for result in (world.host_first, world.host_second):
        assert not Path(result.provenance.build_dir).resolve().is_relative_to(REPO.resolve())
        assert not Path(result.provenance.build_dir).resolve().is_relative_to(PROJECT.resolve())
