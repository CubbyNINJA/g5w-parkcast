Draft requirements for the receiver's parking gate on the head unit. The stage root is
STAGE_ROOT. This is for a later head-unit slice (S2); nothing here runs in S1.

Read `docs/spec/DECISIONS.md` (OD-01, OD-22, OD-23 and OD-24 are the ones that matter here),
`docs/spec/AMBIGUITY_REGISTER.md` (AMB-24 and AMB-25 are open), `docs/spec/REQUIREMENTS.md`
(AIR-001 to AIR-015 exist; use the next free IDs), `specs/README.md`,
`evidence/airplay-receiver-feasibility.md` (its gaps table has a ParkGuard row), and `AGENTS.md`'s
target facts.

Facts from the owner's unit (260206 CA):
- The body-controller service `com.hkmc.micom.bcm` (`com.hkmc.bcm.IBCMService`) has
  `getGearStatusInfo` (transaction 14) and `getParkBrakeStatus` (7), and its listener
  `IBCMListener` has `onGearStatusInfoChanged` (19) and `onParkBrakeStatusChanged` (14). An
  installed third-party app on this unit (engine.sound) already reads vehicle data through the
  stock manager classes by method name, not by transaction number.
- The stock video lockout is `com.hkmc.regulation`. DiPO listens for
  `android.mobis.regulation.ACTION_EXIT_REGULATION`.
- A NEMESIS package is a zip that installs under `/data/nemesis/apps/<id>/`.

The owner's decisions to encode exactly:
- OD-23: video plays only in Park. Mirrored audio is allowed outside Park.
- OD-24: that parking requirement is the **default**. It's a **configurable option**: changing a
  flag in the package turns it off. Audio stays allowed outside Park either way.

Draft requirements that cover:
1. The gate with the flag on (the default): when video may show, how leaving Park stops video at
   once while audio continues, what happens when the gear signal is unavailable, erroring or
   unknown (video doesn't show), and an unknown firmware build.
2. The flag: its default, what a missing, unreadable or unrecognised value means, and when a
   change takes effect. The owner hasn't decided the flag's location, name or timing (AMB-24), or
   whether the off state is shown on screen (AMB-25), so cite those as proposals and leave the
   choice to the owner.
3. That the gate only ever reads vehicle signals: it sends nothing to the car and changes no
   vehicle setting (SCOPE.md's driver-assistance and CAN exclusions).
4. Logging: the gate's state and the flag's value at every start, and each transition.

Use acceptance-test tiers per `specs/README.md`: `@offline` for gate and flag logic with fake
signals, and `@unit` for the on-car check (shifting in and out of Park, parked and stationary).
List anything the owner must decide under "Open questions".
