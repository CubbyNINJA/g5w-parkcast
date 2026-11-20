package parkcast.core.port;

/** The stock VehicleInfoManager.getGearPStatus() call (AIR-058). It may block, throw, or fail to link. */
public interface GearSource {
    /** 1 in Park, 0 out of it; any other value is not a Park reading. */
    int getGearPStatus() throws Exception;
}
