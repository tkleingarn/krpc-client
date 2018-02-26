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

        // leader lights trigger change in flight model
        // if vessels are flying to target with SAS, no need to update autopilot every X ms
        Stream<Boolean> lights = null;
        try {
            lights = connection.addStream(leadControl, "getLights");
        } catch (StreamException e) {
            e.printStackTrace();
        }
        lights.addCallback(
                (Boolean x) -> {
                    logger.info("Callback triggered, leader lights set to {}", x.toString());

                        logger.info("leader lights {}, targeting leader", x.toString());
                        try {
                            for (SpaceCenter.Vessel vessel : vessels) {
                                SpaceCenter.Control vesselControl = null;
                                spaceCenter.setActiveVessel(vessel);
                                spaceCenter.setTargetVessel(leader);
                                vesselControl = vessel.getControl();
                                if(x) {
                                    vesselControl.setSASMode(SpaceCenter.SASMode.TARGET);
                                } else {
                                    vesselControl.setSASMode(SpaceCenter.SASMode.STABILITY_ASSIST);
                                }
                            }
                            spaceCenter.setActiveVessel(leader);
                        } catch (RPCException e) {
                            e.printStackTrace();
                        }


                    // lights on, follow the leader
                    while(x) {
                        logger.info("Lights should be on - leader lights set to {}", x.toString());
                        for (SpaceCenter.Vessel vessel : vessels) {
                            SpaceCenter.Control vesselControl = null;
                            SpaceCenter.AutoPilot vesselAutoPilot = null;
                            try {
                                if(!leadControl.getLights()) {
                                    break;
                                }
                                vesselControl = vessel.getControl();
                                vesselAutoPilot = vessel.getAutoPilot();
                                setNonDirectionalControls(leader, vessel, vesselControl, leadControl);
                                vesselAutoPilot.engage();
                            } catch (RPCException e) {
                                e.printStackTrace();
                            }
                        }
                        try {
                            Thread.sleep(leadPollingIntervalMillis);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    // could set brake control here but let's be cool
                    // each callback will need to update the config and apply it

                    // lights off - hopefully the callback will interrupt this loop
                    while(!x) {
                        logger.info("Lights should be off - leader lights set to", x.toString());
                        for (SpaceCenter.Vessel vessel : vessels) {
                            SpaceCenter.Control vesselControl = null;
                            SpaceCenter.AutoPilot vesselAutoPilot = null;

                            try {
                                if(leadControl.getLights()) {
                                    break;
                                }
                                vesselControl = vessel.getControl();
                                vesselAutoPilot = vessel.getAutoPilot();
                                if (!vessel.equals(leader)) {
                                    // set non-directional controls
                                    setNonDirectionalControls(leader, vessel, vesselControl, leadControl);

                                    // squadron flight
                                    vesselControl.setSASMode(SpaceCenter.SASMode.STABILITY_ASSIST);

                                    // set flight telemetry targets
                                    vesselAutoPilot.setTargetPitch(leadFlightTelemetry.getPitch());
                                    vesselAutoPilot.setTargetRoll(leadFlightTelemetry.getRoll());
                                    vesselAutoPilot.setTargetHeading(leadFlightTelemetry.getHeading());
                                    vesselAutoPilot.setTargetDirection(leadFlightTelemetry.getDirection());
                                    vesselAutoPilot.engage();
                                }
                            } catch (IllegalArgumentException e) {
                                e.printStackTrace();
                                logger.error("Vessel {} missing, removing from squadron vessels.", vessel.toString());
                                // vessels.remove(vessel); //throws ConcurrentModificationException
                            } catch (RPCException e){
                                e.printStackTrace();
                            }
                        }
                        // if the above does not work we could check leader lights and break out of the loop
                        try {
                            Thread.sleep(leadPollingIntervalMillis);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } // end while loop polling leader
                }); // end callback
        lights.start();
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