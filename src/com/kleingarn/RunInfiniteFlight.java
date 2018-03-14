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

import static com.kleingarn.FuelUtils.dropEmptyTanks;
import static com.kleingarn.FuelUtils.getDecoupleableParts;

public class RunInfiniteFlight {

    final static Logger logger = LoggerFactory.getLogger(RunInfiniteFlight.class);

    final static String leaderName = "squad_blue_00";
    final static String squadronName = "squad_blue";

    // Triplet<Double, Double, Double> = pitch, roll, yaw

    // default value is 0.5 seconds for each axis
    // maximum amount of time that the vessel should need to come to a complete stop
    // limits the maximum angular velocity of the vessel
    final static Triplet<Double, Double, Double> stoppingTime = new Triplet<>(1.0, 1.0, 1.0);

    // default value is 5 seconds for each axis
    // smaller value will make the autopilot turn the vessel towards the target more quickly
    // decreasing the value too much could result in overshoot
    final static Triplet<Double, Double, Double> decelerationTime = new Triplet<>(5.0, 5.0, 5.0);

    // default is 3 seconds in each axis
    final static Triplet<Double, Double, Double> timeToPeak = new Triplet<>(2.8, 2.8, 2.8);

    // default value is 1 degree in each axis
    final static Triplet<Double, Double, Double> attenuationAngle = new Triplet<>(1.0, 3.0, 1.0);
    final static boolean tweakAp = true;

    final static String waterBiome = "Water";
    final static String shoresBiome = "Shores";
    final static String highlandsBiome = "Highlands";
    final static String mountainsBiome = "Mountains";

    final static int pollingIntervalMillis = 1000;
    final static int turnTimeMillis = 500;
    final static int maxTurns = 2;
    final static float headingChangeOverWater = 65.0f;
    final static float headingChangeOverLand = 20.0f;
    final static int minLevelOutCount = 5;
    final static int maxLevelOutCount = 20;
    final static double minAltitudeAboveSurface = 120;
    final static double maxAltitudeAboveSurface = 300;
    final static double minAltitudeAboveHighlands = 800;
    final static double maxAltitudeAboveHighlands = 1000;
    final static double minAltitudeAboveMountains = 3000;
    final static double maxAltitudeAboveMountains = 10000;

    // level
    // e.g. Current pitch 2.6546106, heading 113.08709, roll -89.046875
    // pitch (float) – Target pitch angle, in degrees between -90° and +90°.
    // heading (float) – Target heading angle, in degrees between 0° and 360°.

    final static float pitchDuringTurn = 2.00f;
    final static float pitchDuringAscent = 20.00f;
    final static float pitchDuringMountainClimb = 60.00f;
    final static float pitchDuringDescent = -4.00f;
    final static float leftRollDuringTurn = -30f;
    final static float rightRollDuringTurn = 30f;

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Flight");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        // assume we are flying already
        SpaceCenter.Vessel vessel = spaceCenter.getActiveVessel();
        flyTrackingCoast(spaceCenter, vessel);
    }

    private static void flyTrackingCoast(SpaceCenter spaceCenter, SpaceCenter.Vessel vessel) {

        SpaceCenter.Control vesselControl = null;
        SpaceCenter.AutoPilot vesselAutoPilot = null;
        List<SpaceCenter.Part> partsWithDecouplers = null;
        Triplet<Float, Float, Float> pitchHeadingRoll = null;

        try {
            vesselControl = vessel.getControl();
            vesselAutoPilot = vessel.getAutoPilot();
        } catch (RPCException e) {
            e.printStackTrace();
        }

// /*
        int numTurns = 0;
        while (true) {
            try {
                String biome = vessel.getBiome();
                logger.info("Vessel biome is {}", biome);

                pitchHeadingRoll = PitchHeadingRoll.getPitchHeadingRoll(spaceCenter, vessel);
                logger.info("Current pitch {}, heading {}, roll {}",
                        pitchHeadingRoll.getValue0(),
                        pitchHeadingRoll.getValue1(),
                        pitchHeadingRoll.getValue2());
                float currentHeading = pitchHeadingRoll.getValue1();

                if (numTurns < maxTurns) {
                    if (biome.equals(shoresBiome) || biome.equals(waterBiome)) {
                        // if you drift over water
                        logger.info("Over {}, turning left.", biome);
                        turn(vessel, vesselAutoPilot, vesselControl,
                                pitchDuringTurn,
                                (currentHeading - headingChangeOverWater),
                                leftRollDuringTurn);
                        numTurns++;
                    } else { // if you drift over land
                        logger.info("Over land, turning right.");
                        turn(vessel, vesselAutoPilot, vesselControl,
                                pitchDuringTurn,
                                (currentHeading + headingChangeOverLand),
                                rightRollDuringTurn);
                        numTurns++;
                    }
                } else {
                    applyCamera(spaceCenter);
                    partsWithDecouplers = getDecoupleableParts(vessel);
                    dropEmptyTanks(vessel, partsWithDecouplers);

                    int randomLevelOutCount = ThreadLocalRandom.current().nextInt(
                            minLevelOutCount,
                            maxLevelOutCount + 1);
                    logger.info("Leveling out for {} turns.", randomLevelOutCount);
                    for (int j=0; j<randomLevelOutCount; j++) {
                        turn(vessel, vesselAutoPilot, vesselControl, 2.0f, currentHeading, 0);
                    }
                    numTurns = 0;
                }

            } catch (RPCException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
       // */ // testing roll
    }

    private static void turn(SpaceCenter.Vessel vessel,
                            SpaceCenter.AutoPilot vesselAutoPilot,
                            SpaceCenter.Control vesselControl,
                            float targetPitch,
                            float targetHeading,
                            float targetRoll) {
        logger.info("Turning with pitch {}, heading {}, roll {}", targetPitch, targetHeading, targetRoll);
        try {
            // safety check
            while (tooLow(vessel)) {
                vesselAutoPilot.setTargetRoll(0);
                if (!vessel.getBiome().equals(highlandsBiome) && !vessel.getBiome().equals(mountainsBiome)) {
                    vesselAutoPilot.setTargetPitch(pitchDuringAscent);
                } else {
                    vesselAutoPilot.setTargetPitch(pitchDuringMountainClimb);
                }
                vesselAutoPilot.setTargetHeading(targetHeading);
                vesselAutoPilot.engage();
                sleep(turnTimeMillis);
            }
            while (tooHigh(vessel)){
                vesselAutoPilot.setTargetRoll(0);
                if (!vessel.getBiome().equals(highlandsBiome) && !vessel.getBiome().equals(mountainsBiome)) {
                    vesselAutoPilot.setTargetPitch(pitchDuringDescent);
                } else {
                    vesselAutoPilot.setTargetPitch(pitchDuringDescent);
                }
                vesselAutoPilot.setTargetHeading(targetHeading);
                vesselAutoPilot.engage();
                sleep(turnTimeMillis);
            }

            // roll first
            vesselAutoPilot.setTargetRoll(targetRoll);
            vesselAutoPilot.engage();
            sleep(turnTimeMillis);

            // then turn
            vesselAutoPilot.setTargetPitch(targetPitch);
            vesselAutoPilot.setTargetHeading(targetHeading);
            vesselAutoPilot.engage();
            sleep(turnTimeMillis);

            vesselAutoPilot.disengage();
        } catch (RPCException e) {
            e.printStackTrace();
        }
    }

    private static boolean tooLow(SpaceCenter.Vessel vessel) {
        try {
            SpaceCenter.Flight vesselFlight = vessel.flight(vessel.getSurfaceReferenceFrame());

            double surfaceAltitude = vesselFlight.getSurfaceAltitude();
            double meanAltitude = vesselFlight.getMeanAltitude();
            double elevation = vesselFlight.getElevation();
            double bedrockAltitude = vesselFlight.getBedrockAltitude();
            logger.info("Altitude {}, surface altitude {}, elevation {}, bedrock altitude {}",
                    meanAltitude, surfaceAltitude, elevation, bedrockAltitude);

            if((!vessel.getBiome().equals(highlandsBiome) && !vessel.getBiome().equals(mountainsBiome))) {
                if (surfaceAltitude < minAltitudeAboveSurface) {
                    logger.info("Surface altitude of {} is too low, pull up!", surfaceAltitude);
                    return true;
                } else {
                    logger.info("Surface altitude of {} is safe, no need to adjust.", surfaceAltitude);
                    return false;
                }
            } else if (vessel.getBiome().equals(highlandsBiome)){
                if (surfaceAltitude < minAltitudeAboveHighlands) {
                    logger.info("Altitude above highlands {} is too low, pull up!", surfaceAltitude);
                    return true;
                } else {
                    logger.info("Altitude above highlands {} is safe, no need to adjust.", surfaceAltitude);
                    return false;
                }
            } else if (vessel.getBiome().equals(mountainsBiome)){
                if (surfaceAltitude < minAltitudeAboveMountains) {
                    logger.info("Altitude above mountains {} is too low, pull up!", surfaceAltitude);
                    return true;
                } else {
                    logger.info("Altitude above mountains {} is safe, no need to adjust.", surfaceAltitude);
                    return false;
                }
            }
        } catch (RPCException e) {
            e.printStackTrace();
        }
        logger.info("All altitude checks failed, proceeding with no change.");
        return false;
    }

    private static boolean tooHigh(SpaceCenter.Vessel vessel) {
        try {
            SpaceCenter.Flight vesselFlight = vessel.flight(vessel.getSurfaceReferenceFrame());
            double surfaceAltitude = vesselFlight.getSurfaceAltitude();

            if((!vessel.getBiome().equals(highlandsBiome) && !vessel.getBiome().equals(mountainsBiome))) {
                if (surfaceAltitude > maxAltitudeAboveSurface) {
                    logger.info("Surface altitude of {} is too high, descending.", surfaceAltitude);
                    return true;
                } else {
                    logger.info("Surface altitude of {} is fine, no need to adjust.", surfaceAltitude);
                    return false;
                }
            } else if(vessel.getBiome().equals(highlandsBiome)) {
                if (surfaceAltitude > maxAltitudeAboveHighlands) {
                    logger.info("Altitude above highlands {} is too high, descending.", surfaceAltitude);
                    return true;
                } else {
                    logger.info("Altitude above highlands {} is safe, no need to adjust.", surfaceAltitude);
                    return false;
                }
            } else if(vessel.getBiome().equals(mountainsBiome)){
                if (surfaceAltitude > maxAltitudeAboveMountains) {
                    logger.info("Altitude above mountains {} is too high, descending.", surfaceAltitude);
                    return true;
                } else {
                    logger.info("Altitude above mountains {} is safe, no need to adjust.", surfaceAltitude);
                    return false;
                }
            }
        } catch (RPCException e) {
            e.printStackTrace();
        }
        logger.info("All altitude checks failed, proceeding with no change.");
        return false;
    }


    private static void applyCamera(SpaceCenter spaceCenter) {

            try {
                // Camera minPitch = -91.67324 and maxPitch = 88.80845
                SpaceCenter.Camera camera = spaceCenter.getCamera();
                int randomHeading = ThreadLocalRandom.current().nextInt(
                        0,
                        360 + 1);
                int randomPitch = ThreadLocalRandom.current().nextInt(
                        0,
                        88 + 1 - 50);
                int randomDistance = ThreadLocalRandom.current().nextInt(
                        50,
                        250 + 1);
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