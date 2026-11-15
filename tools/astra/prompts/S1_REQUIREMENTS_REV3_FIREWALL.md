Revise slice S1's requirements for an owner decision that removes scripted firewall changes. The
stage root is STAGE_ROOT.

Read `docs/spec/DECISIONS.md` (OD-31 is new; it supersedes the scripted firewall rule in OD-05,
OD-27 and OD-29's firewall clauses), `docs/spec/REQUIREMENTS.md` (AIR-001 to AIR-031, AT-01 to
AT-31), `specs/README.md` (tiers, including `@host`) and `docs/spec/S1_INTERFACES.md`.

OD-31, to encode exactly:
- The runner makes no firewall changes: it creates, changes and removes no rule.
- When UxPlay first starts listening, Windows' own firewall prompt asks the owner, and the owner
  allows it on this PC's Public network (OD-06).
- At the end of every run, the runner reminds the owner how to remove that allowance (Windows
  Defender Firewall > Allowed apps), and records that the firewall was handled by the owner.
- The owner removes the allowance.

Draft:
1. Replacement wording for AIR-003, AIR-004, AIR-005 and AIR-030, or their retirement, with
   reasons. Keep the IDs stable: a retired row stays in the table, marked retired by OD-31.
2. Changes to any other row that depends on the scripted rule: AIR-006 (cleanup on launch or
   capture failure), AIR-007(c) (stop UxPlay, then firewall cleanup), AIR-031(d)-(f) and OD-29's
   preflight, and AIR-014 if it's affected.
3. Matching acceptance-test changes for AT-03, AT-04, AT-05, AT-30 and any others affected,
   with their tiers. Say which `@host` firewall checks remain, if any.
4. What the end-of-run reminder must say and where it's recorded, as testable statements.
5. Anything OD-31 leaves open (for example, what the runner should do if the owner declines the
   Windows prompt, or if no prompt appears because an allowance already exists), as open
   questions for the owner, not as requirements.
