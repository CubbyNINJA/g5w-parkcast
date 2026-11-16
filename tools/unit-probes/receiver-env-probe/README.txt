receiver-env-probe - READ-ONLY, under a minute. Facts for S3: Wi-Fi interfaces and addresses, whether
the hotspot runs with CarPlay off, the mdnsd socket and library, and the H.264 decoder list. Never reads
Wi-Fi config files. Guarded to build 260206.
RUN: head unit on, CarPlay NOT connected. Copy this folder's NEMESIS.data to the drive root, insert, tap
Yes, wait for "Receiver env probe done". Removes the root NEMESIS.data when it starts.
Output: NEMESIS-diag/receiver-env-probe/. Never commit it.
