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

public class RunSquadronBombingRun {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    final static String leaderName = "bomber_lead";
    final static String squadronName = "bomber";

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Squadron flight");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        Squadron squad = Squadron.buildSquadron(
                squadronName,
                leaderName,
                spaceCenter);

        int leadPollingIntervalMillis = 10;
        SpaceCenter.Vessel leader = squad.getSquadLeader();
        List<SpaceCenter.Vessel> vessels = squad.getSquadronVessels();
        SpaceCenter.Control leadControl = leader.getControl();
        SpaceCenter.Flight leadFlightTelemetry = leader.flight(leader.getSurfaceReferenceFrame());

        logger.info("##### Built squadron from available active vessels #####");
        logger.info("squadron name: {}", squad.getSquadronName());
        logger.info("squad leader: {}", squad.getSquadLeader().getName());
        logger.info("squadron peeps: {}", squad.getSquadronVessels().stream().count());

        // v1 impl, listen for changes from leader using callbacks, unused here
        // squad.getAndSetUpdatesFromLeader(spaceCenter, connection);

        // Triplet<Double, Double, Double> = pitch, roll, yaw

        // default value is 0.5 seconds for each axis
        // maximum amount of time that the vessel should need to come to a complete stop
        // limits the maximum angular velocity of the vessel
        Triplet<Double, Double, Double> stoppingTime = new Triplet<>(1.0, 1.0, 1.0);

        // default value is 5 seconds for each axis
        // smaller value will make the autopilot turn the vessel towards the target more quickly
        // decreasing the value too much could result in overshoot
        Triplet<Double, Double, Double> decelerationTime = new Triplet<>(5.0, 5.0, 5.0);

        // default is 3 seconds in each axis
        Triplet<Double, Double, Double> timeToPeak = new Triplet<>(2.8, 2.8, 2.8);

        // default value is 1 degree in each axis
        Triplet<Double, Double, Double> attenuationAngle = new Triplet<>(1.0, 1.0, 1.0);

        boolean tweakAp = true;

        // v2
        // periodically get all config from leader and apply to squadron
        logger.info("Updating autopilot for squad every {} ms", leadPollingIntervalMillis);
        while (true) {
            squad.getSquadronVessels().parallelStream().forEach(v -> {
                SpaceCenter.Control vesselControl = null;
                SpaceCenter.AutoPilot vesselAutoPilot = null;
                try {
                    vesselControl = v.getControl();
                    vesselAutoPilot = v.getAutoPilot();
                    if (!v.equals(leader)) {
                        if (tweakAp) {
                            vesselAutoPilot.setStoppingTime(stoppingTime);
                            vesselAutoPilot.setDecelerationTime(decelerationTime);
                            vesselAutoPilot.setTimeToPeak(timeToPeak);
                            vesselAutoPilot.setAttenuationAngle(attenuationAngle);
                        }
                        // stage
                        if (vesselControl.getCurrentStage() < leadControl.getCurrentStage()) {
                            vesselControl.activateNextStage();
                        }

                        // set non-directional controls
                        setNonDirectionalControls(vesselControl, leadControl);

                        // set flight telemetry targets
                        vesselAutoPilot.setTargetPitch(leadFlightTelemetry.getPitch());
                        vesselAutoPilot.setTargetRoll(leadFlightTelemetry.getRoll());
                        vesselAutoPilot.setTargetHeading(leadFlightTelemetry.getHeading());
                        vesselAutoPilot.setTargetDirection(leadFlightTelemetry.getDirection());
                        vesselAutoPilot.engage();
                    }
                } catch(RPCException e){
                    e.printStackTrace();
                }
            });

            // if leader had opened bomb bay
            if(leadControl.getActionGroup(2)) {
                int currentStage = leader.getControl().getCurrentStage();
                logger.info("Current stage of leader is " + currentStage);
                squad.getSquadronVessels().parallelStream().forEach(v -> {
                    try {
                        v.getControl().toggleActionGroup(2);
                        int myCurrentStage = currentStage;
                        releaseBombs(v, v.getControl(), myCurrentStage);
                    } catch (RPCException e) {
                        e.printStackTrace();
                    }
                });
            }
            sleep(leadPollingIntervalMillis);
        }
    }

    public static void releaseBombs(SpaceCenter.Vessel vessel, SpaceCenter.Control vesselControl, int currentStage) {
        try {
            logger.info("Releasing bombs for vessel " + vessel.getName() + " and stage " + currentStage);
            // check that bay doors are fully open
            boolean allBaysOpen = false;
            List<SpaceCenter.CargoBay> cargoBays = vessel.getParts().getCargoBays();
            while(!allBaysOpen) {
                logger.info("Waiting for all bays to open on vessel {}", vessel.getName());
                for(SpaceCenter.CargoBay bay : cargoBays) {
                    logger.info("Cargo bay {} is {}", bay, bay.getOpen());
                    if (!bay.getOpen()) {
                        allBaysOpen = false;
                        sleep(1000);
                    } else {
                        allBaysOpen = true;
                        logger.info("All cargo bays are open on vessel {}, dropping bombs", vessel.getName());
                        break;
                    }
                }
            }

            List<SpaceCenter.Decoupler> allDecouplers = vessel.getParts().getDecouplers();
            logger.info("Current vessel " + vessel.getName() + " has " + allDecouplers.size() + " decouplers");

            int totalDecouplerCount = allDecouplers.size();
            while(totalDecouplerCount > 0) {
                logger.info("Vessel {} has {} decouplers", vessel.getName(), allDecouplers.size());
                for (SpaceCenter.Decoupler decoupler : allDecouplers) {
                    logger.info("Vessel {}, decoupler {}, stage {}, target stage {}", vessel.getName(), decoupler, decoupler.getPart().getDecoupleStage(), currentStage);
                    if (decoupler.getPart().getDecoupleStage() == (currentStage - 1)) {
                        logger.info("Decoupling {} on vessel {}", decoupler.getPart(), vessel.getName());
                        decoupler.decouple();
                        totalDecouplerCount--;
                        currentStage--;
                        sleep(500);
                    }
                }
            }
            // close cargo bay
            vesselControl.toggleActionGroup(2);
        } catch (RPCException e) {
            e.printStackTrace();
        }
        sleep(1000);
    }

    public static void setNonDirectionalControls(SpaceCenter.Control vesselControl,
                                   SpaceCenter.Control leadControl) {
        try {
            vesselControl.setBrakes(leadControl.getBrakes());
            vesselControl.setSAS(leadControl.getSAS());
            vesselControl.setGear(leadControl.getGear());
            vesselControl.setThrottle(leadControl.getThrottle());
        } catch (RPCException e) {
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