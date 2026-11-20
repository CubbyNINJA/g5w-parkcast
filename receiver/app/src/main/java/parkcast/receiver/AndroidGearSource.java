package parkcast.receiver;

import parkcast.core.port.GearSource;

/**
 * {@link GearSource}, not yet wired to the unit. The Park reading needs the OEM vehicle-info class, whose package
 * name the whole-app scan forbids (AMB-98, open). Every call throws, so {@link parkcast.core.adapter.ParkCall}
 * reports the reading unavailable and the gate keeps video blocked: the fail-safe outcome.
 */
final class AndroidGearSource implements GearSource {
    @Override
    public int getGearPStatus() throws Exception {
        throw new UnsupportedOperationException("gear access not wired");
    }
}
