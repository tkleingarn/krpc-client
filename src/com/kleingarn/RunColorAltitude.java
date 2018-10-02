package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.Drawing;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.javatuples.Quartet;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.print.Doc;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RunColorAltitude {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

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

        // consider 26,000m max air breathing altitude;
        Triplet<Double, Double, Double> customHighlightColor = new Triplet<>(1.0, 1.0, 1.0);
        SpaceCenter.Flight vesselFlightTelemetry;
        Double elevation;
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
                //float getPitch()
                //The pitch of the vessel relative to the horizon, in degrees. A value between -90° and +90°.
                logger.info("Pitch: " + pitch);

                for (SpaceCenter.Part part : parts) {

                    // red
                    if (elevation < redElevationThreshold) {
                        logger.info("RED");
                        customHighlightColor = new Triplet<>(
                                1.0,
                                0.0,
                                0.0);
                    }
                    // yellow
                    else if (elevation >= redElevationThreshold && elevation < yellowElevationThreshold) {
                        logger.info("Between red and yellow");
                        // customHighlightColor = new Triplet<>(1.0, 1.0, 0.0); full yellow
                        customHighlightColor = new Triplet<>(
                                1.0,
                                1.0,
                                0.0);
                    }
                    // green
                    else if (elevation >= yellowElevationThreshold && elevation < greenElevationThreshold) {
                        logger.info("Between yellow and green");
                        customHighlightColor = new Triplet<>(
                                1 - (elevation / greenElevationThreshold),
                                1.0,
                                0.0);
                    }
                    // cyan
                    else if (elevation >= greenElevationThreshold && elevation < blueElevationThreshold) {
                        logger.info("Between green and blue");
                        customHighlightColor = new Triplet<>(
                                0.0,
                                1 - (elevation / blueElevationThreshold),
                                1.0);
                    }
                    // cyan
                    else if (elevation >= blueElevationThreshold && elevation < edgeOfInnerAtmosphereThreshold) {
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
                    else if (elevation > outerAtmosphereThreshold) {
                        logger.info("You are in space now");
                        customHighlightColor = new Triplet<>(1.0, 1.0, 1.0);
                    }

                    logger.info("Setting custom highlight color " + customHighlightColor.getValue0() +
                            ", " + customHighlightColor.getValue1() +
                            ", " + customHighlightColor.getValue2());
                    part.setHighlightColor(customHighlightColor);
                    part.setHighlighted(true);
                }

                final Triplet<Double, Double, Double> notLevelCustomHighlightColor = new Triplet<>(-customHighlightColor.getValue0(),
                        -customHighlightColor.getValue1(),
                        -customHighlightColor.getValue2());

                int msAtInvertedColor = (int) Math.abs(pitch * 10);
                int msAtMainColor = 1000 - msAtInvertedColor;
                logger.info("ms main color: " + msAtMainColor + " and ms at inverted color: " + msAtInvertedColor);

                sleep(msAtMainColor);
                if (pitch <= -1 || pitch >= 1) {
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
                }
                sleep(msAtInvertedColor);

//                Triplet<Double, Double, Double> textPosition = new Triplet(0.0, 0.0, 0.0);
//                Quartet<Double, Double, Double, Double> textRotation = new Quartet(0.0, 90.0, 180.0, 0.0);
//
//                drawing.clear(true);
//
//                drawing.addText(
//                        String.valueOf((int) elevation.shortValue()),
//                        vessel.getSurfaceReferenceFrame(),
//                        textPosition,
//                        textRotation,
//                        true);
            } else {
                for (SpaceCenter.Part part : parts) {
                    part.setHighlighted(false);
                }
                sleep(1000);
            }
        }
    }

    // TextaddText(String text, SpaceCenter.ReferenceFrame referenceFrame,
    // org.javatuples.Triplet<Double, Double, Double> position,
    // org.javatuples.Quartet<Double, Double, Double, Double> rotation,
    // boolean visible)



    private static void sleep (int sleepTimeInmillis) {
        try {
            Thread.sleep(sleepTimeInmillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}