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
    final static Triplet<Double, Double, Double> attenuationAngle = new Triplet<>(1.0, 1.0, 1.0);
    final static boolean tweakAp = true;

    final static String waterBiome = "Water";

    final static int pollingIntervalMillis = 1000;
    final static int turnTimeMillis = 3000;

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

    final static float pitchDuringTurn = 3.00f;
    final static float headingChange = 30.0f;
    final static float leftRollDuringTurn = -50.0f;
    final static float rightRollDuringTurn = 170.0f;


    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Squadron flight");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        // assume we are flying already
        SpaceCenter.Vessel vessel = spaceCenter.getActiveVessel();

        // fly
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

                // if you drift over water
                if (biome.equals(waterBiome)) {
                    logger.info("Over water");
                    turn(vesselAutoPilot, vesselControl,
                            pitchDuringTurn,
                            (currentHeading - headingChange),
                            leftRollDuringTurn);
                    currentHeading = currentHeading - headingChange;
                } else { // if you drift over land
                    logger.info("Over land");
                    turn(vesselAutoPilot, vesselControl,
                            pitchDuringTurn,
                            (currentHeading + headingChange),
                            rightRollDuringTurn);
                    currentHeading = currentHeading + headingChange;
                }
                logger.info("Levelling out.");
                turn(vesselAutoPilot, vesselControl, 1.00f, currentHeading, 0);
            } catch (RPCException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // if you drift over land
            // TODO: tkleingarn

            // wait leadPollingIntervalMillis
            sleep(pollingIntervalMillis);
        }
    }

    public static void turn(SpaceCenter.AutoPilot vesselAutoPilot,
                            SpaceCenter.Control vesselControl,
                            float targetPitch,
                            float targetHeading,
                            float targetRoll) {
        logger.info("Turning with pitch {}, heading {}, roll {}", targetPitch, targetHeading, targetRoll);
        try {
            vesselAutoPilot.setTargetPitch(targetPitch);
            vesselAutoPilot.setTargetHeading(targetHeading);
//            vesselAutoPilot.setTargetRoll(targetRoll);
            vesselAutoPilot.engage();
            sleep(turnTimeMillis);
            vesselAutoPilot.disengage();
            vesselControl.setSAS(true);
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