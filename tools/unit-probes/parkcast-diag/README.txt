parkcast-diag - about 2.5 minutes. Why the first S3 run showed a black screen and wrote no receiver log.
Reads ParkCast's logcat lines, its open files and ports, the log folder permissions, the SELinux mode and
the focused window, and copies receiver.log if one exists. Never copies the identity key or reads Wi-Fi
config files. If ParkCast isn't running it starts it, exactly as parkcast-install does. Guarded to 260206.
RUN: parked in P, iPhone Bluetooth off (no CarPlay), iPhone on the car's Wi-Fi. Copy this folder's
NEMESIS.data to the drive root, insert, tap Yes. At "cast now", mirror from the iPhone to ParkCast and
keep casting until "ParkCast diag done". Removes the root NEMESIS.data when it starts.
Output: NEMESIS-diag/parkcast-diag/. Never commit it (it can name the iPhone).
