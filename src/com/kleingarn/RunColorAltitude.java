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

public class RunColorAltitude {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    private enum elevationThreshold {
        RED,
        YELLOW,
        GREEN,
        CYAN,
        BLUE,
        WHITE
    }

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Color pulse");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        Drawing drawing = Drawing.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        // mk2SpacePlaneAdapter
        // mk2Cockpit.Inline
        // mk2Fuselage
        // mk2.1m.Bicoupler
        // turboFanEngine
        // turboFanEngine

        SpaceCenter.Vessel vessel = spaceCenter.getActiveVessel();
        List<SpaceCenter.Part> parts = vessel.getParts().getAll();
        DockingUtils.printParts(parts);

        List<String> pitchIndicatorPartNames = new ArrayList<>();
        pitchIndicatorPartNames.add("shockConeIntake");
        pitchIndicatorPartNames.add("mk2SpacePlaneAdapter");
        pitchIndicatorPartNames.add("mk2Cockpit.Inline");
        pitchIndicatorPartNames.add("mk2Fuselage");
        pitchIndicatorPartNames.add("mk2.1m.Bicoupler");
        pitchIndicatorPartNames.add("turboFanEngine");

        // Create list of pitch indicating parts
        List<SpaceCenter.Part> pitchIndicatorParts = new ArrayList<>();
        for(SpaceCenter.Part part : parts) {
            for(String partName : pitchIndicatorPartNames) {
                if (part.getName().equals(partName)) {
                    pitchIndicatorParts.add(part);
                    logger.info("Matching part {} found, adding to pitchIndicatorParts", part.getName());
                }
            }
        }

        vessel.getControl().setLights(false);
        Triplet<Double, Double, Double> customHighlightColor = new Triplet<>(1.0, 1.0, 1.0);
        SpaceCenter.Flight vesselFlightTelemetry;
        Double elevation;
        elevationThreshold currentElevationRange = elevationThreshold.RED;
        elevationThreshold previousElevationRange = elevationThreshold.RED;
        Double prctOfMax;
        float pitch;

        //colors
        final int redElevationThreshold = 200;
        final int yellowElevationThreshold = 1000;
        final int greenElevationThreshold = 7000;
        final int blueElevationThreshold = 19000;
        final int edgeOfInnerAtmosphereThreshold = 26000;
        final int outerAtmosphereThreshold = 55000;

        while(true) {
            if(vessel.getControl().getActionGroup(5)) {

                vesselFlightTelemetry = vessel.flight(vessel.getSurfaceReferenceFrame());
                elevation = vesselFlightTelemetry.getSurfaceAltitude(); //getSurfaceAltitude instead of getMeanAltitude
                logger.info("Current elevation is " + elevation);
                prctOfMax = elevation / edgeOfInnerAtmosphereThreshold;
                logger.info("prctOfMax is " + prctOfMax);

                pitch = vesselFlightTelemetry.getPitch();
                //The pitch of the vessel relative to the horizon, in degrees. A value between -90° and +90°.
                logger.info("Pitch: " + pitch);

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
                try {
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

                final Triplet<Double, Double, Double> notLevelCustomHighlightColor = new Triplet<>(-customHighlightColor.getValue0(),
                        -customHighlightColor.getValue1(),
                        -customHighlightColor.getValue2());

                // flash when changing stages
                if(checkElevationChange(previousElevationRange, currentElevationRange)) {
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

                // optionally flash inverted color to indicate pitch
                if(vessel.getControl().getActionGroup(6)) {
                    int msAtInvertedColor = (int) Math.abs(pitch * 10);
                    int msAtMainColor = 1000 - msAtInvertedColor;
                    logger.info("ms main color: " + msAtMainColor + " and ms at inverted color: " + msAtInvertedColor);

                    sleep(msAtMainColor);
                    if (pitch <= -1 || pitch >= 1) {
                        vessel.getControl().setLights(true);
                        pitchIndicatorParts.parallelStream().forEach(p -> {
                            try {
                                logger.info("Part {} is a pitch indicator, checking for flash", p.getName());
                                p.setHighlighted(false);
                                p.setHighlightColor(notLevelCustomHighlightColor);
                                p.setHighlighted(true);
                            } catch (RPCException e) {
                                e.printStackTrace();
                            }
                        });
                    } else {
                        vessel.getControl().setLights(false);
                    }
                    sleep(msAtInvertedColor);
                }
            } else {
                for (SpaceCenter.Part part : parts) {
                    part.setHighlighted(false);
                    vessel.getControl().setLights(false);
                }
                sleep(1000);
            }
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