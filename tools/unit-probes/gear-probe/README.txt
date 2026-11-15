gear-probe - READ-ONLY, 90 seconds. Measures how this build reports Park for the AirPlay
receiver's parking gate. Once a second it calls two body-controller GETTERS only (gear status
and parking brake status), guarded to build 260206. Commands nothing, but the gear call makes the
BCM service send a status request to the head unit's MICOM (docs/evidence/GEAR_PROBE_FINDINGS.md).
DO IT PARKED: car on, stationary, foot firmly on the brake the whole time.
RUN: copy this folder's NEMESIS.data to the drive root, insert, tap Yes, then shift as the toasts
say (or by stopwatch from the moment you tap Yes):
   0-15 s P   |  15-30 s R  |  30-45 s N  |  45-60 s D  |  60-90 s back to P
Wait for "Gear probe done". Removes the root NEMESIS.data when it starts.
Output: NEMESIS-diag/gear-probe/ (readings, logcat lines, and the unit's build/version
properties, with serial, IMEI, MAC and VIN keys left out).
