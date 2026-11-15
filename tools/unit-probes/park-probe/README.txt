park-probe - READ-ONLY, 45 seconds. Checks the stock Park getter the AirPlay receiver's parking
gate would use. Once a second it calls getGearPStatus on the system service, which returns the
service's cached Park state and sends no request to the MICOM. Calls nothing else. Guarded to
build 260206. Also saves BOOTCLASSPATH and the shared-library list.
DO IT PARKED: car on, stationary, foot firmly on the brake the whole time.
RUN: copy this folder's NEMESIS.data to the drive root, insert, tap Yes, then shift as the toasts
say (or by stopwatch from the moment you tap Yes):
   0-15 s P   |  15-30 s R  |  30-45 s back to P
Wait for "Park probe done". Removes the root NEMESIS.data when it starts.
Output: NEMESIS-diag/park-probe/ (readings, class path, service list, gear logcat lines).
