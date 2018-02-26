package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.Stream;
import krpc.client.StreamException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class RunSquadronChase {

    final static Logger logger = LoggerFactory.getLogger(RunSquadronChase.class);

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

        int leadPollingIntervalMillis = 5;
        SpaceCenter.Vessel leader = squad.getSquadLeader();
        List<SpaceCenter.Vessel> vessels = squad.getSquadronVessels();
        SpaceCenter.Control leadControl = leader.getControl();
        SpaceCenter.Flight leadFlightTelemetry = leader.flight(leader.getSurfaceReferenceFrame());

        logger.info("##### Built squadron from available active vessels #####");
        logger.info("squadron name: {}", squad.getSquadronName());
        logger.info("squad leader: {}", squad.getSquadLeader().getName());
        logger.info("squadron peeps: {}", squad.getSquadronVessels().stream().count());

        // for each vessel set target to leader, set autopilot parameters
        for (SpaceCenter.Vessel vessel : vessels) {

            if (!vessel.equals(leader)) {
                spaceCenter.setActiveVessel(vessel);
                spaceCenter.setTargetVessel(leader); // UnsupportedOperationException: Cannot set SAS mode of vessel

                SpaceCenter.AutoPilot vesselAutoPilot = null;
                if (tweakAp) {
                    try {
                        vesselAutoPilot = vessel.getAutoPilot();
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                    vesselAutoPilot.setStoppingTime(stoppingTime);
                    vesselAutoPilot.setDecelerationTime(decelerationTime);
                    vesselAutoPilot.setTimeToPeak(timeToPeak);
                    vesselAutoPilot.setAttenuationAngle(attenuationAngle);
                }
            }
        }
        spaceCenter.setActiveVessel(leader);


        boolean lights = leadControl.getLights();
        logger.info("Starting control system, leader lights are {}.", lights);
        while(true) {

            // if leader's lights have changed from last iteration, iterate through planes and change SASMode
            if(lights != leadControl.getLights()) {
                logger.info("Detected change in leader lights, changing flight modes for squadron.");
                lights = leadControl.getLights();
                try {
                    for (SpaceCenter.Vessel vessel : vessels) {
                        spaceCenter.setActiveVessel(vessel);
                        logger.info("Active vessel {}", vessel);
                        spaceCenter.setTargetVessel(leader);
                        logger.info("Target vessel {}", leader);
                        SpaceCenter.Control vesselControl = vessel.getControl();
                        if (lights == true) {
                            logger.info("Lights {}, setting vessel {} SASMode to TARGET", lights, vessel);
                            vesselControl.setSASMode(SpaceCenter.SASMode.TARGET);
                        } else {
                            logger.info("Lights {}, setting vessel {} SASMode to STABILITY_ASSIST", lights, vessel);
                            vesselControl.setSASMode(SpaceCenter.SASMode.STABILITY_ASSIST);
                        }
                    }
                    // back to leader
                    spaceCenter.setActiveVessel(leader);
                } catch (RPCException e) {
                    e.printStackTrace();
                }
            }

            // SASMode TARGET = chase the leader, only apply non-directional controls and rely on SAS target for flight
            if(lights == true) {
                logger.info("lights = {}, chasing leader", lights);
                for (SpaceCenter.Vessel vessel : vessels) {
                    SpaceCenter.Control vesselControl = null;
                    SpaceCenter.AutoPilot vesselAutoPilot = null;
                    if (!vessel.equals(leader)) {
                        try {
                            vesselControl = vessel.getControl();
                            vesselAutoPilot = vessel.getAutoPilot();
                            setNonDirectionalControls(leader, vessel, vesselControl, leadControl);
                            vesselAutoPilot.engage();
                        } catch (RPCException e) {
                            e.printStackTrace();
                        }
                    }
                }
                try {
                    Thread.sleep(leadPollingIntervalMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // SASMode STABILITY_ASSIST = squadron flight, mimic the leader, apply all flight controls
            else if(lights == false) {
                logger.info("lights = {}, mimicking leader", lights);
                for (SpaceCenter.Vessel vessel : vessels) {
                    SpaceCenter.Control vesselControl = null;
                    SpaceCenter.AutoPilot vesselAutoPilot = null;
                    if (!vessel.equals(leader)) {
                        try {
                            vesselControl = vessel.getControl();
                            vesselAutoPilot = vessel.getAutoPilot();

                            // set non-directional controls
                            setNonDirectionalControls(leader, vessel, vesselControl, leadControl);
                            // set flight telemetry targets
                            vesselAutoPilot.setTargetPitch(leadFlightTelemetry.getPitch());
                            vesselAutoPilot.setTargetRoll(leadFlightTelemetry.getRoll());
                            vesselAutoPilot.setTargetHeading(leadFlightTelemetry.getHeading());
                            vesselAutoPilot.setTargetDirection(leadFlightTelemetry.getDirection());
                            vesselAutoPilot.engage();
                        } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        logger.error("Vessel {} missing, removing from squadron vessels.", vessel.toString());
                        // vessels.remove(vessel); //throws ConcurrentModificationException
                        } catch (RPCException e) {
                        e.printStackTrace();
                        }
                    }
                }
            }
            // wait leadPollingIntervalMillis
            try {
                Thread.sleep(leadPollingIntervalMillis);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void setNonDirectionalControls(SpaceCenter.Vessel leader,
                                                 SpaceCenter.Vessel vessel,
                                                 SpaceCenter.Control vesselControl,
                                                 SpaceCenter.Control leadControl) {
        try {
            vesselControl.setBrakes(leadControl.getBrakes());
            vesselControl.setSAS(leadControl.getSAS());
            vesselControl.setGear(leadControl.getGear());
            vesselControl.setThrottle(leadControl.getThrottle());

            List<SpaceCenter.Engine> engines = vessel.getParts().getEngines();
            engines.stream().forEach(x -> {
                try {
                    if(x.getPart().getEngine().getHasModes() == true){
                        // 0,1 is turboJet
                        x.setMode(leader.getParts().getEngines().get(0).getMode());
                    }
                } catch (RPCException e) {
                    e.printStackTrace();
                }
            });

        } catch (RPCException e) {
            e.printStackTrace();
        }
    }
}