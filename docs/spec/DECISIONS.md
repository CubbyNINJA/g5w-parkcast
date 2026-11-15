# Owner decision register

Only decisions the owner made are recorded here, quoted or closely paraphrased from the owner's
own messages, with the date. Proposals and open questions are in `AMBIGUITY_REGISTER.md`.
Scenarios cite these IDs where a decision is what they assert.

| ID | Date | Decision | Source |
|---|---|---|---|
| OD-01 | 2026-11-14 | Build an AirPlay screen-mirroring receiver app for the head unit, so the owner can watch phone apps such as YouTube on the head unit's screen "when parked and waiting for someone". It ships as a NEMESIS package. | Owner messages: "launch apps not typically available on carplay ... like youtube or netflix when parked and wiating for someone"; "further explore the Airplay Mirroring app" |
| OD-02 | 2026-11-14 | All development uses an SDD and BDD process like SilphCo Lambda 2.0's: decisions, then Astra-drafted requirements, then Gherkin scenarios, then an Astra coverage review applied before tests, then tests first, then implementation. GPT-6 Astra through the Codex CLI drafts requirements and reviews coverage. | Owner: "do all development on this in a SDD and BDD framework, similar to SilphCo Lambda 2.0"; reviewer choice: "Astra via Codex" |
| OD-03 | 2026-11-14 | The project lives inside hmg_nemesis's 1.16 tree, `C:\Users\Gaming\nemesis-rebase`, on a new branch `chore/airplay-receiver`, committed locally and never pushed. hmg_nemesis's `AGENTS.md` §10 rule against a Gherkin layer is amended for this project; the owner approves the wording. | Owner choices: "Inside hmg_nemesis, change §10", "nemesis-rebase, 1.16", "Chore branch, never pushed" |
| OD-04 | 2026-11-14 | Spec first, then test: record the decisions, write the feasibility requirements and a scenario for the H.264 test, get them reviewed, then build UxPlay and run the test on this PC as that scenario's evidence. | Owner choice: "Spec first, then test" |
| OD-05 | 2026-11-14 | For the PC test, install MSYS2 (about 1–2 GB in `C:\msys64`, via winget) with GStreamer, build UxPlay 1.74 from source, and add a Windows Firewall inbound rule for `uxplay.exe` only, removed after the test. MSYS2 was installed and updated on 2026-11-14; nothing else has been installed yet. | Owner choice: "Yes, all of it" |
| OD-06 | 2026-11-14 | The PC test runs with this PC wired to the home network (`192.168.1.100`, Ethernet) and the owner's iPhone on the home Wi-Fi of the same network. | Owner: "same network, but the computer is wired" |
