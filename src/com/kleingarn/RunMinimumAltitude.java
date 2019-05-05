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

    final static int pollingIntervalMillis = 1000;
    final static double minAltitudeAboveSurface = 1;
    final static double maxAltitudeAboveSurface = 2;

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
        double surfaceAltitude = vesselFlight.getSurfaceAltitude();

        while (true) {
            // if min flight mode activated
            if (vesselControl.getActionGroup(5)) {

                surfaceAltitude = vesselFlight.getSurfaceAltitude();

                if(surfaceAltitude > maxAltitudeAboveSurface) {
                    vesselControl.setActionGroup(1, true);
                } else {
                    vesselControl.setActionGroup(1, false);
                }
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