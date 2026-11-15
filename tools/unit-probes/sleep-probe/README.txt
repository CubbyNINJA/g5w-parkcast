sleep-probe - READ-ONLY, two short runs. How the head unit reports going to sleep and waking, for
the AirPlay receiver. Each run saves the uptime, the sticky ACC-status broadcast, two read-only
power-state getters and the power-related logcat lines. Calls nothing else; guarded to build 260206.
RUN 1: car on, parked. Copy this folder's NEMESIS.data to the drive root, insert, tap Yes. When the
       message says so, switch the car OFF, wait 30 seconds, and switch it back ON.
RUN 2: once the car is on again, unplug the stick and plug it back in, then tap Yes again.
Run 2 removes the root NEMESIS.data. Output: NEMESIS-diag/sleep-probe/ (run1, run2).
