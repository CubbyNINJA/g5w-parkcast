coexist-probe - READ-ONLY, about 3 minutes. For the AirPlay receiver's coexistence with CarPlay.
Takes two snapshots of the unit's listening ports, the processes that own them, the network
interfaces and the mDNS/CarPlay processes: one with wireless CarPlay off, one with it on. Reads
/proc, ps, netcfg and getprop only: no service calls, nothing sent to the car, no Wi-Fi
configuration or password read. Guarded to build 260206.
DO IT PARKED, with wireless CarPlay DISCONNECTED when you tap Yes.
RUN: copy this folder's NEMESIS.data to the drive root, insert, tap Yes. When the toast says
"connect wireless CarPlay now", connect it and leave it connected until "Coexist probe done"
(about 2 minutes later). Removes the root NEMESIS.data when it starts.
Output: NEMESIS-diag/coexist-probe/ (A-carplay-off, B-carplay-on, logcat lines).
