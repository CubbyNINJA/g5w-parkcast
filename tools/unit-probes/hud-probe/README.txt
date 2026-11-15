hud-probe - READ-ONLY, about a minute. Reads the HUD's settings (on/off, content selection
including turn-by-turn and AV info) through the stock settings service's GETTERS only, and
records the display layout (screens, sizes, density) for the AirPlay receiver project.
It calls nothing unless the service is com.hkmc.usm.IUSMService and the build is exactly 260206
(the numbers were read from this unit's own framework). Never sets anything.
RUN: copy this folder's NEMESIS.data to the drive root, insert, tap Yes. Car on; the HUD on
or off as you normally have it. Removes the root NEMESIS.data when it starts.
Output: NEMESIS-diag/hud-probe/.
