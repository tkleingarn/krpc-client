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

    final static int pollingIntervalMillis = 1000;
    final static int turnTimeMillis = 5000;
    final static int maxTurns = 3;
    final static double minAltitudeAboveSurface = 500;
    final static double maxAltitudeAboveSurface = 1000;

    // level
    // Current pitch 2.6546106, heading 113.08709, roll -89.046875
    // turning left
    // Current pitch 2.729361, heading 85.088234, roll -34.88261
    //                                            full left roll = -0.00
    // turning right
    // Current pitch 5.564002, heading 107.941185, roll -143.168
    //                                             full right roll = -180
    // targets
    // pitch 5, heading +/- 30, roll -30 left, -150 right

    // pitch (float) – Target pitch angle, in degrees between -90° and +90°.
    // heading (float) – Target heading angle, in degrees between 0° and 360°.

    final static float pitchDuringTurn = 2.00f;
    final static float headingChange = 25.0f;
    final static float leftRollDuringTurn = -45f;
    final static float rightRollDuringTurn = 45f;


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
        Triplet<Float, Float, Float> pitchHeadingRoll = null;

        try {
            vesselControl = vessel.getControl();
            vesselAutoPilot = vessel.getAutoPilot();
            vesselControl.setSAS(true);
        } catch (RPCException e) {
            e.printStackTrace();
        }
/*
        // testing roll
        while (true) {
            try {
                logger.info("Current roll is {} ", (float) vesselControl.getRoll());
                sleep(pollingIntervalMillis);
            } catch (RPCException e) {
                e.printStackTrace();
            }
        }
*/
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
                    // if you drift over water
                    if (biome.equals(shoresBiome) || biome.equals(waterBiome)) {
                        logger.info("Over {}, turning left.", biome);
                        turn(vessel, vesselAutoPilot, vesselControl,
                                pitchDuringTurn,
                                (currentHeading -= headingChange),
                                leftRollDuringTurn);
                        numTurns++;
                    } else { // if you drift over land
                        logger.info("Over land, turning right.");
                        turn(vessel, vesselAutoPilot, vesselControl,
                                pitchDuringTurn,
                                (currentHeading += headingChange),
                                rightRollDuringTurn);
                        numTurns++;
                    }
                } else {
                    logger.info("Leveling out.");
                    for (int j=0; j<3; j++) {
                        turn(vessel, vesselAutoPilot, vesselControl, 4.00f, currentHeading, 0);
                    }
                    numTurns = 0;
                    applyCamera(spaceCenter);
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

    public static void turn(SpaceCenter.Vessel vessel,
                            SpaceCenter.AutoPilot vesselAutoPilot,
                            SpaceCenter.Control vesselControl,
                            float targetPitch,
                            float targetHeading,
                            float targetRoll) {
        logger.info("Turning with pitch {}, heading {}, roll {}", targetPitch, targetHeading, targetRoll);
        try {
            // safety check
            while (tooLow(vessel)){
                vesselAutoPilot.setTargetRoll(0);
                vesselAutoPilot.setTargetPitch(20);
                vesselAutoPilot.setTargetHeading(targetHeading);
                vesselAutoPilot.engage();
                sleep(turnTimeMillis);
            }
            while (tooHigh(vessel)){
                vesselAutoPilot.setTargetRoll(0);
                vesselAutoPilot.setTargetPitch(-10);
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
            vesselControl.setSAS(true);
        } catch (RPCException e) {
            e.printStackTrace();
        }
    }

    public static boolean tooLow(SpaceCenter.Vessel vessel) {
        try {
            SpaceCenter.Flight vesselFlight = vessel.flight(vessel.getSurfaceReferenceFrame());

            double surfaceAltitude = vesselFlight.getSurfaceAltitude();
            double meanAltitude = vesselFlight.getMeanAltitude();
            double elevation = vesselFlight.getElevation();
            double bedrockAltitude = vesselFlight.getBedrockAltitude();
            logger.info("Altitude {}, surface altitude {}, elevation {}, bedrock altitude {}",
                    meanAltitude, surfaceAltitude, elevation, bedrockAltitude);

            if (surfaceAltitude < minAltitudeAboveSurface) {
                logger.info("Surface altitude of {} is too low, pull up!", surfaceAltitude);
                return true;
            } else {
                logger.info("Surface altitude of {} is safe, no need to adjust.", surfaceAltitude);
                return false;
            }
        } catch (RPCException e) {
            e.printStackTrace();
        }
        logger.info("All altitude checks failed, proceeding with no change.");
        return false;
    }

    public static boolean tooHigh(SpaceCenter.Vessel vessel) {
        try {
            SpaceCenter.Flight vesselFlight = vessel.flight(vessel.getSurfaceReferenceFrame());
            double surfaceAltitude = vesselFlight.getSurfaceAltitude();

            if (surfaceAltitude > maxAltitudeAboveSurface) {
                logger.info("Surface altitude of {} is too high, descend!", surfaceAltitude);
                return true;
            } else {
                logger.info("Surface altitude of {} is fine, no need to adjust.", surfaceAltitude);
                return false;
            }
        } catch (RPCException e) {
            e.printStackTrace();
        }
        logger.info("All altitude checks failed, proceeding with no change.");
        return false;
    }

    // Camera minPitch = -91.67324 and maxPitch = 88.80845
    public static void applyCamera(SpaceCenter spaceCenter) {

        try {

                SpaceCenter.Camera camera = spaceCenter.getCamera();
                logger.info("Camera minPitch = {} and maxPitch = {}", 0, camera.getMaxPitch());
                logger.info("Camera minDistance = {} and maxDistance = {}", camera.getMinDistance(), camera.getMaxDistance());
                int randomPitch = ThreadLocalRandom.current().nextInt(
                        0,
                        60 + 1);
                int randomDistance = ThreadLocalRandom.current().nextInt(
                        (int) camera.getMinDistance() * 2,
    //                    (int) camera.getMaxDistance() / 10 + 1);
                        200 + 1);
                camera.setPitch(randomPitch);
                camera.setDistance(randomDistance);
        } catch (RPCException e) {
            e.printStackTrace();
        }

    }

    public static void sleep (int sleepTimeInmillis) {
        try {
            Thread.sleep(sleepTimeInmillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}