package parkcast.receiver;

import parkcast.gate.port.Display;
import parkcast.gate.port.Frame;
import parkcast.gate.port.MirrorSession;
import parkcast.gate.port.VehicleBus;

/**
 * The gate ports the receiver never acts on (AIR-067). Each method does nothing. The inner {@link Display} does
 * nothing because core's codec display renders through the decoder's output buffers.
 */
final class NoCalls {
    private NoCalls() {}

    static final class NoDisplay implements Display {
        @Override
        public void show(Frame f) {}

        @Override
        public void clear() {}
    }

    static final class NoMirrorSession implements MirrorSession {
        @Override
        public void muteAudio() {}

        @Override
        public void stopAudio() {}

        @Override
        public void disconnectAudio() {}

        @Override
        public void stopConnection() {}

        @Override
        public void disconnect() {}

        @Override
        public void reconnect() {}
    }

    static final class NoBus implements VehicleBus {
        @Override
        public void sendCommand(String command) {}

        @Override
        public void writeCan(int id, byte[] data) {}

        @Override
        public void changeSetting(String name, String value) {}

        @Override
        public void changeRegulation(String state) {}

        @Override
        public void sendRegulationEvent(String event) {}

        @Override
        public void routeFrameToHud(Frame f) {}

        @Override
        public void changeDriverAssistanceDisplay(String setting) {}

        @Override
        public void registerParkListener(Object listener) {}

        @Override
        public void requestBcmStatus() {}
    }
}
