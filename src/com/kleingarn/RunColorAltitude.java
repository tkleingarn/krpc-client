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

public class RunColorAltitude {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Color pulse");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        SpaceCenter.Vessel vessel = spaceCenter.getActiveVessel();
        List<SpaceCenter.Part> parts = vessel.getParts().getAll();

        // consider 26,000m max air breathing altitude;
        Triplet<Double, Double, Double> customHighlightColor = new Triplet<>(1.0, 1.0, 1.0);
        SpaceCenter.Flight vesselFlightTelemetry;
        Double elevation;
        Double prctOfMax;

        //colors
        final int redElevationThreshold = 200;
        final int yellowElevationThreshold = 1000;
        final int greenElevationThreshold = 7000;
        final int blueElevationThreshold = 19000;
        final int edgeOfInnerAtmosphereThreshold = 26000;
        final int outerAtmosphereThreshold = 55000;

        while(true) {
            if(vessel.getControl().getActionGroup(5)) {
                for (SpaceCenter.Part part : parts) {

                    vesselFlightTelemetry = vessel.flight(vessel.getSurfaceReferenceFrame());
                    elevation = vesselFlightTelemetry.getMeanAltitude();
                    logger.info("Current elevation is " + elevation);
                    prctOfMax = elevation / edgeOfInnerAtmosphereThreshold;
                    logger.info("prctOfMax is " + prctOfMax);

                    // red
                    if(elevation < redElevationThreshold) {
                        logger.info("RED");
                        customHighlightColor = new Triplet<>(
                                1.0,
                                0.0,
                                0.0);
                    }
                    // yellow
                    else if(elevation >= redElevationThreshold && elevation < yellowElevationThreshold) {
                        logger.info("Between red and yellow");
                        // customHighlightColor = new Triplet<>(1.0, 1.0, 0.0); full yellow
                        customHighlightColor = new Triplet<>(
                                1.0,
                                1.0,
                                0.0);
                    }
                    // green
                    else if(elevation >= yellowElevationThreshold && elevation < greenElevationThreshold) {
                        logger.info("Between yellow and green");
                        customHighlightColor = new Triplet<>(
                                1 - (elevation / greenElevationThreshold),
                                1.0,
                                0.0);
                    }
                    // cyan
                    else if(elevation >= greenElevationThreshold && elevation < blueElevationThreshold) {
                        logger.info("Between green and blue");
                        customHighlightColor = new Triplet<>(
                                0.0,
                                1 - (elevation / blueElevationThreshold),
                                1.0);
                    }
                    // cyan
                    else if(elevation >= blueElevationThreshold && elevation < edgeOfInnerAtmosphereThreshold) {
                        logger.info("Between blue and edgeOfInner");
                        customHighlightColor = new Triplet<>(
                                0.0,
                                1 - (elevation / edgeOfInnerAtmosphereThreshold),
                                (elevation / edgeOfInnerAtmosphereThreshold));
                    }
                    // blue
                    else if (elevation >= edgeOfInnerAtmosphereThreshold && elevation < outerAtmosphereThreshold) {
                        logger.info("Outer atmosphere, setting color to blue for all parts");
                        customHighlightColor = new Triplet<>(
                                0.0,
                                0.0,
                                1 - (elevation / outerAtmosphereThreshold));
                    }
                    // white
                    else if (elevation > outerAtmosphereThreshold){
                        logger.info("You are in space now");
                        customHighlightColor = new Triplet<>(1.0, 1.0, 1.0);
                    }

                    logger.info("Setting custom highlight color " + customHighlightColor.getValue0() +
                            ", " + customHighlightColor.getValue1() +
                            ", " + customHighlightColor.getValue2());
                    part.setHighlightColor(customHighlightColor);
                    part.setHighlighted(true);
                }
            } else {
                for (SpaceCenter.Part part : parts) {
                    part.setHighlighted(false);
                }
            }
            sleep(1000);
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