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
    final static double targetAltitudeAboveSurface = 2.5; //110

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
        float maxPitchChangeAscending = 0.000001F;
        float pitchChangePerIntervalAscending = 0.0000005F;

        double targetAltitudeChangePerPollingInterval = 0.10; //0.01m per 100ms interval
        boolean descending = false;
        int i = 0;

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
                    logger.info("Ascending at {}", pitch);
                }
            } else {
                logger.info("Normal flight");
                vesselControl.setPitch(0);
            }
            sleep(pollingIntervalMillis);
            priorSurfaceAltitude = vesselFlight.getSurfaceAltitude();
            altititudeDiffThisInterval = currentSurfaceAltitude - priorSurfaceAltitude;
            logger.info("Altitude diff this interval is {}", altititudeDiffThisInterval);
            if(i == 30) {
                applyCamera(spaceCenter);
                i = 0;
            }
            i++;
        }
    }

    private static void applyCamera(SpaceCenter spaceCenter) {

        try {
            // Camera minPitch = -91.67324 and maxPitch = 88.80845
            SpaceCenter.Camera camera = spaceCenter.getCamera();
            int randomHeading = ThreadLocalRandom.current().nextInt(
                    0,
                    360 + 1);
            int randomPitch = ThreadLocalRandom.current().nextInt(
                    -5,
                    30 + 1);
            int randomDistance = ThreadLocalRandom.current().nextInt(
                    10,
                     75 + 1);
            camera.setHeading(randomHeading);
            camera.setPitch(randomPitch);
            camera.setDistance(randomDistance);
        } catch(RPCException e){
            e.printStackTrace();
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