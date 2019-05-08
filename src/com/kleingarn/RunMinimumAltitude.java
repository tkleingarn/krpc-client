package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.kleingarn.FuelUtils.getDecoupleableParts;

public class RunMinimumAltitude {

    final static Logger logger = LoggerFactory.getLogger(RunMinimumAltitude.class);

    final static int pollingIntervalMillis = 100;
    final static double minAltitudeAboveSurface = 10; //100
    final static double maxAltitudeAboveSurface = 15; //110

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Flight");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        // assume we are flying already
        SpaceCenter.Vessel vessel = spaceCenter.getActiveVessel();
        SpaceCenter.Control vesselControl = vessel.getControl();

        SpaceCenter.Flight vesselFlight = vessel.flight(vessel.getSurfaceReferenceFrame());
        double surfaceAltitude;

        while (true) {
            // if min flight mode activated
            if (vesselControl.getActionGroup(5)) {

                surfaceAltitude = vesselFlight.getSurfaceAltitude();
                logger.info("Min flight mode active, surface altitude is: {}", surfaceAltitude);
                if(surfaceAltitude > maxAltitudeAboveSurface) {
                    // vesselControl.setActionGroup(1, true); // not this simple
                    // need to:
                    // 1) switch to docking mode

                    // 2) activate the 'i' key for downward RCS, or possibly 'w' for pitch down while in docking mode
//                    vesselControl.setInputMode(SpaceCenter.ControlInputMode.OVERRIDE);
//                    vesselControl.setUp(-1);
                    vesselControl.setPitch(-0.19F);
                    logger.info("setUp -1 to descend");
                } else if (surfaceAltitude <= minAltitudeAboveSurface) {
//                    vesselControl.setInputMode(SpaceCenter.ControlInputMode.OVERRIDE);
//                    vesselControl.setUp(0);
                    vesselControl.setPitch(0.25F);
                    logger.info("setUp 1 to climb");
                }
            } else {
                vesselControl.setInputMode(SpaceCenter.ControlInputMode.ADDITIVE);
            }
           sleep(pollingIntervalMillis);
        }
    }

    private static void sleep (int sleepTimeInmillis) {
        try {
            Thread.sleep(sleepTimeInmillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}