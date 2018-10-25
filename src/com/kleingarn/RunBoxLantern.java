package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.Drawing;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class RunBoxLantern {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    private enum elevationThreshold {
        RED,
        YELLOW,
        GREEN,
        CYAN,
        BLUE,
        WHITE
    }

    // final static String squadronName = "lantern octagon 02 Probe";
    final static String vesselPrefix = "lantern octagon 02 Probe";

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Color pulse");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        List<SpaceCenter.Vessel> allVessels = spaceCenter.getVessels();
        logger.info("##### Listing all active vessels #####");
        Squadron.printActiveVesselsFromList(allVessels);

        // find squadron
        List<SpaceCenter.Vessel> squadronVessels = allVessels.stream().filter(v -> {
            try {
                return (v.getName().contains(vesselPrefix) && !v.getName().contains("Debris"));
            } catch (RPCException e) {
                e.printStackTrace();
            }
            return false;}).collect(toList());
        logger.info("##### Listing vessels in squadron #####");
        Squadron.printActiveVesselsFromList(squadronVessels);

        while(true) {
            for (SpaceCenter.Vessel vessel : squadronVessels) {
                try {
                    logger.info("Highlighting parts on vessel {}", vessel.getName());
                    highlightBasedOnElevation(vessel);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    squadronVessels.remove(vessel);
                }
            }
            sleep(1000);
        }
    }

    private static void highlightBasedOnElevation(SpaceCenter.Vessel vessel) {

        SpaceCenter.Flight vesselFlightTelemetry;
        Double elevation = 0.0;
        elevationThreshold currentElevationRange = elevationThreshold.RED;
        elevationThreshold previousElevationRange = elevationThreshold.RED;
        Double prctOfMax;

        //colors
        final int redElevationThreshold = 200;
        final int yellowElevationThreshold = 1000;
        final int greenElevationThreshold = 7000;
        final int blueElevationThreshold = 19000;
        final int edgeOfInnerAtmosphereThreshold = 26000;
        final int outerAtmosphereThreshold = 55000;

        Triplet<Double, Double, Double> customHighlightColor = new Triplet<>(1.0, 1.0, 1.0);

        try {
            vesselFlightTelemetry = vessel.flight(vessel.getSurfaceReferenceFrame());
            elevation = vesselFlightTelemetry.getSurfaceAltitude(); //getSurfaceAltitude instead of getMeanAltitude
            logger.info("Current elevation is " + elevation);
            prctOfMax = elevation / edgeOfInnerAtmosphereThreshold;
            logger.info("prctOfMax is " + prctOfMax);
        } catch (RPCException e) {
            e.printStackTrace();
        }

        // red
        if (elevation < redElevationThreshold) {
            logger.info("RED");
            customHighlightColor = new Triplet<>(
                    1.0,
                    0.0,
                    0.0);
            previousElevationRange = currentElevationRange;
            currentElevationRange = elevationThreshold.RED;
        }
        // yellow
        else if (elevation >= redElevationThreshold && elevation < yellowElevationThreshold) {
            logger.info("Between red and yellow");
            // customHighlightColor = new Triplet<>(1.0, 1.0, 0.0); full yellow
            customHighlightColor = new Triplet<>(
                    1.0,
                    1.0,
                    0.0);
            previousElevationRange = currentElevationRange;
            currentElevationRange = elevationThreshold.YELLOW;
        }
        // green
        else if (elevation >= yellowElevationThreshold && elevation < greenElevationThreshold) {
            logger.info("Between yellow and green");
            customHighlightColor = new Triplet<>(
                    1 - (elevation / greenElevationThreshold),
                    1.0,
                    0.0);
            previousElevationRange = currentElevationRange;
            currentElevationRange = elevationThreshold.GREEN;
        }
        // cyan
        else if (elevation >= greenElevationThreshold && elevation < blueElevationThreshold) {
            logger.info("Between green and blue");
            customHighlightColor = new Triplet<>(
                    0.0,
                    1 - (elevation / blueElevationThreshold),
                    1.0);
            previousElevationRange = currentElevationRange;
            currentElevationRange = elevationThreshold.CYAN;
        }
        // cyan
        else if (elevation >= blueElevationThreshold && elevation < edgeOfInnerAtmosphereThreshold) {
            logger.info("Between blue and edgeOfInner");
            customHighlightColor = new Triplet<>(
                    0.0,
                    1 - (elevation / edgeOfInnerAtmosphereThreshold),
                    (elevation / edgeOfInnerAtmosphereThreshold));
            previousElevationRange = currentElevationRange;
            currentElevationRange = elevationThreshold.BLUE;
        }
        // blue
        else if (elevation >= edgeOfInnerAtmosphereThreshold && elevation < outerAtmosphereThreshold) {
            logger.info("Outer atmosphere, setting color to blue for all parts");
            customHighlightColor = new Triplet<>(
                    0.0,
                    0.0,
                    1 - (elevation / outerAtmosphereThreshold));
            previousElevationRange = currentElevationRange;
            currentElevationRange = elevationThreshold.BLUE;
        }
        // white
        else if (elevation > outerAtmosphereThreshold) {
            logger.info("You are in space now");
            customHighlightColor = new Triplet<>(1.0, 1.0, 1.0);
            previousElevationRange = currentElevationRange;
            currentElevationRange = elevationThreshold.WHITE;
        }

        // highlight all parts
        logger.info("Highlighting all parts with color: " + customHighlightColor.getValue0() +
                ", " + customHighlightColor.getValue1() +
                ", " + customHighlightColor.getValue2());
        int i = 0;
        List<SpaceCenter.Part> parts = null;
        // List<SpaceCenter.Part> parts = vessel.getParts().getAll();
        try {
            parts = vessel.getParts().getAll();
            for (SpaceCenter.Part part : parts) {
                part.setHighlightColor(customHighlightColor);
                part.setHighlighted(true);
                i++;
            }
        } catch (RPCException e) {
            e.printStackTrace();
            // remove missing parts
            parts.remove(i);
        }

        // flash when changing stages
        if (checkElevationChange(previousElevationRange, currentElevationRange)) {
            parts.parallelStream().forEach(p -> {
                try {
                    p.setHighlighted(false);
                    sleep(50);
                    p.setHighlighted(true);
                } catch (RPCException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static boolean checkElevationChange(elevationThreshold previousElevationRange, elevationThreshold currentElevationRange) {
        if(!previousElevationRange.equals(currentElevationRange)) {
            logger.info("Elevation change detected from {} to {}", previousElevationRange.toString(), currentElevationRange.toString());
            return true;
        } else {
            return false;
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