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
    final static double minAltitudeAboveSurface = 4; //100
    final static double maxAltitudeAboveSurface = 6; //110
    final static double targetAltitudeAboveSurface = 20; //110

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Flight");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        // assume we are flying already
        SpaceCenter.Vessel vessel = spaceCenter.getActiveVessel();
        SpaceCenter.Control vesselControl = vessel.getControl();
        SpaceCenter.AutoPilot vesselAutoPilot = vessel.getAutoPilot();

        SpaceCenter.Flight vesselFlight = vessel.flight(vessel.getSurfaceReferenceFrame());

        double currentSurfaceAltitude;
        double priorSurfaceAltitude = 200000;
        double altititudeDiffThisInterval = 0.00;
        float pitch = 0.00F;

        float pitchChangePerIntervalDescending = 0.03F; // money on descent
        float maxPitchChangeDescending = 0.30F;

        // float maxPitchChange = 0.15F;
        float maxPitchChangeAscending = 0.001F;
        float pitchChangePerIntervalAscending = 0.0005F;

        double targetAltitudeChangePerPollingInterval = 0.10; //0.01m per 100ms interval
        boolean descending = false;

        while (true) {
            currentSurfaceAltitude = vesselFlight.getSurfaceAltitude();
            priorSurfaceAltitude = currentSurfaceAltitude;

            // if min flight mode activated
            if (vesselControl.getActionGroup(5)) {

                vesselControl.setRoll(0);
                currentSurfaceAltitude = vesselFlight.getSurfaceAltitude();
                logger.info("Flight mode active, surface altitude is: {}, prior surface altitude was {}", currentSurfaceAltitude, priorSurfaceAltitude);

                // descend
                if(currentSurfaceAltitude > targetAltitudeAboveSurface) {
                    if(!descending) {
                        pitch = 0;
                        descending = true;
                        logger.info("DESCENDING!!!");
                    }
                    // vesselControl.setPitch(-0.17F);
                    if(altititudeDiffThisInterval < Math.abs(targetAltitudeChangePerPollingInterval)
                            && Math.abs(pitch) <= maxPitchChangeDescending) {
                        pitch = pitch - pitchChangePerIntervalDescending;
                    }
                    logger.info("Setting negative pitch");
                    vesselControl.setPitch(pitch);
//                    vesselAutoPilot.setTargetPitch(pitch);
//                    vesselAutoPilot.setTargetRoll(0);
                    logger.info("Descending at {}", pitch);
                } else if (currentSurfaceAltitude <= targetAltitudeAboveSurface) {

                    if(descending) {
                        pitch = 0;
                        descending = false;
                        logger.info("ASCENDING!!!");
                    }
                    if(altititudeDiffThisInterval < Math.abs(targetAltitudeChangePerPollingInterval)
                            && pitch <= maxPitchChangeAscending) {
                        pitch = pitch + pitchChangePerIntervalAscending;
                    }
                    vesselControl.setPitch(pitch);
//                    vesselAutoPilot.setTargetPitch(pitch);
//                    vesselAutoPilot.setTargetRoll(0);
                    logger.info("Ascending at {}", pitch);
                }
//                vesselAutoPilot.engage();
            } else {
//                vesselControl.setInputMode(SpaceCenter.ControlInputMode.ADDITIVE);
                logger.info("Normal flight");
//                vesselAutoPilot.disengage();
            }
            sleep(pollingIntervalMillis);
            priorSurfaceAltitude = vesselFlight.getSurfaceAltitude();
            altititudeDiffThisInterval = currentSurfaceAltitude - priorSurfaceAltitude;
            logger.info("Altitude diff this interval is {}", altititudeDiffThisInterval);
        }
    }

    private static void graduallyAscend() {

    }

    private static void graduallyDescend() {

        // target rate of descent is 0.01m per 100ms interval


    }

    // pitch larger and larger until you are increasing at target rate
    // pitch less if your diff exceeds target rate


    private static void sleep (int sleepTimeInmillis) {
        try {
            Thread.sleep(sleepTimeInmillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}