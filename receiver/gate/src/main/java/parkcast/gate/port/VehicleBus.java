package parkcast.gate.port;

/** Every vehicle or HUD interface the receiver can reach. The gate passes nothing to it. */
public interface VehicleBus {
    void sendCommand(String command);

    void writeCan(int id, byte[] data);

    void changeSetting(String name, String value);

    void changeRegulation(String state);

    void sendRegulationEvent(String event);

    void routeFrameToHud(Frame f);

    void changeDriverAssistanceDisplay(String setting);

    void registerParkListener(Object listener);

    void requestBcmStatus();
}
