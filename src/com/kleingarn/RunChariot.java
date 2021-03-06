package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RunChariot {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    static String leaderName = "lead";
    static String pullerName = "puller";
    final static String squadronName = "air";

    // v1 impl, listen for changes from leader using callbacks, unused here
    // squad.getAndSetUpdatesFromLeader(spaceCenter, connection);

    // Triplet<Double, Double, Double> = pitch, roll, yaw

    // default value is 0.5 seconds for each axis
    // maximum amount of time that the vessel should need to come to a complete stop
    // limits the maximum angular velocity of the vessel
    final static Triplet<Double, Double, Double> stoppingTime = new Triplet<>(1.0, 1.0, 1.0);

    // default value is 5 seconds for each axis
    // smaller value will make the autopilot turn the vessel towards the target more quickly
    // decreasing the value too much could result in overshoot
    final static Triplet<Double, Double, Double> decelerationTime = new Triplet<>(15.0, 15.0, 15.0);

    // The target time to peak used to autotune the PID controllers.
    // A vector of three times, in seconds, for each of the pitch, roll and yaw axes. Defaults to 3 seconds for each axis.
    // default is 3 seconds in each axis
    final static Triplet<Double, Double, Double> timeToPeak = new Triplet<>(6.0, 6.0, 6.0);

    // default value is 1 degree in each axis
    final static Triplet<Double, Double, Double> attenuationAngle = new Triplet<>(1.0, 1.0, 1.0);

    // The threshold at which the autopilot will try to match the target roll angle, if any. Defaults to 5 degrees.
    final static double rollThreshold = 5.0;

    final static int leadPollingIntervalMillis = 100;

    final static boolean tweakAp = false;

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Squadron flight");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());


        // separate stages
        spaceCenter.getActiveVessel().getControl().activateNextStage();



        sleep(1000);

        List<SpaceCenter.Vessel> vessels = new ArrayList<>();

        List<SpaceCenter.Vessel> potentialPullerList = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "sensorBarometer");
        for (SpaceCenter.Vessel vessel : potentialPullerList) {
            logger.info("Setting vessel {} name to ", vessel.getName(), pullerName);
            vessel.setName(pullerName);
            vessels.add(vessel);
        }

        SpaceCenter.Vessel leader = null;
        List<SpaceCenter.Vessel> potentialLeadList = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "longAntenna");
        for (SpaceCenter.Vessel vessel : potentialLeadList) {
            logger.info("Setting vessel {} name to ", vessel.getName(), leaderName);
            vessel.setName(leaderName);
            spaceCenter.setActiveVessel(vessel);
            leader = vessel;
            vessels.add(vessel);
        }

        Squadron squad = Squadron.buildSquadron(
                squadronName,
                leaderName,
                spaceCenter);

        for(SpaceCenter.Vessel vessel : squad.getSquadronVessels()) {
            if(!vessel.equals(leader) && !vessel.getName().equals(pullerName)){
                vessels.add(vessel);
            }
        }

        for(SpaceCenter.Vessel v : vessels) {
            if(!v.equals(leader)){
                SpaceCenter.AutoPilot ap = v.getAutoPilot();
                ap.engage();
            }
        }
        SpaceCenter.Control leadControl = leader.getControl();
        SpaceCenter.Flight leadFlightTelemetry = leader.flight(leader.getSurfaceReferenceFrame());

        logger.info("##### Built squadron from available active vessels #####");
//        logger.info("squadron name: {}", squad.getSquadronName());
//        logger.info("squad leader: {}", squad.getSquadLeader().getName());
//        logger.info("squadron peeps: {}", squad.getSquadronVessels().stream().count());

        // v2
        // periodically get all config from leader and apply to squadron
        logger.info("Updating autopilot for squad every {} ms", leadPollingIntervalMillis);

        List<SpaceCenter.Decoupler> allDecouplers = spaceCenter.getActiveVessel().getParts().getDecouplers();
        logger.info("Current vessel has " + allDecouplers.size() + " decouplers");

        while (true) {

            leadControl = leader.getControl();
            if (leader.getControl().getActionGroup(7)) {
                setAutopilotLevelOnSquadron(vessels, leader);
            } else {
                if (leader.getControl().getActionGroup(6)) {
                    setControlTargets(vessels, leader, leadControl);
                } else {
                    setAutopilotTargets(vessels, leader, leadFlightTelemetry, leadControl);
                }
            }

            for (int i=0; i<10; i++) {
                try {
                    setActionGroupsOnSquadron(i, leadControl.getActionGroup(i), vessels);
                } catch (IllegalArgumentException e) {
                    vessels.remove(i);
                    logger.error("Caught IllegalArgumentException, removed vessel from squadron" + i);
                    e.printStackTrace();
                }
            }

            if (leader.getControl().getActionGroup(5)) {
                logger.info("Deploying even chutes");
                deployChutes(vessels);

//                List<SpaceCenter.Vessel> leaderListOne = new ArrayList<SpaceCenter.Vessel>();
//                leaderListOne.add(leader);
//                deployChutes(leaderListOne);

                leadControl.setActionGroup(5, false);
            }

            sleep(leadPollingIntervalMillis);
        }
    }

    public static void setControlTargets(List<SpaceCenter.Vessel> vessels,
                                           SpaceCenter.Vessel leader,
                                           SpaceCenter.Control leadControl) {
        logger.info("Applying lead flight control to all squadron vessels.");
        vessels.parallelStream().forEach(v -> {
            SpaceCenter.Control vesselControl = null;
            try {
                vesselControl = v.getControl();
                if (!v.equals(leader)) {
//                    vesselControl.setInputMode(SpaceCenter.ControlInputMode.OVERRIDE);
                    vesselControl.setPitch(leadControl.getPitch());
                    vesselControl.setRoll(leadControl.getRoll());
                    vesselControl.setYaw(leadControl.getYaw());
                }
            } catch(RPCException e){
                e.printStackTrace();
            } catch(IllegalArgumentException e) {
                e.printStackTrace();
                vessels.remove(v);
            } catch (IllegalMonitorStateException e) {
                e.printStackTrace();
            }
        });
    }

    public static void setAutopilotTargets(List<SpaceCenter.Vessel> vessels,
                                           SpaceCenter.Vessel leader,
                                           SpaceCenter.Flight leadFlightTelemetry,
                                           SpaceCenter.Control leadControl) {
        logger.info("Applying lead flight telemetry to autopilot for all squadron vessels.");
        vessels.parallelStream().forEach(v -> {
            if(!v.equals(leader)) {
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
                            vesselAutoPilot.setRollThreshold(rollThreshold);
                        }
                        // stage
                        if (vesselControl.getCurrentStage() < leadControl.getCurrentStage()) {
                            vesselControl.activateNextStage();
                        }

                        // set non-directional controls
                        setNonDirectionalControls(vesselControl, leadControl);

                        // set flight telemetry targets
                        vesselAutoPilot.setTargetPitch(leadFlightTelemetry.getPitch());
                        // logger.info("lead pitch {}", leadFlightTelemetry.getPitch());

                        vesselAutoPilot.setRollThreshold(rollThreshold);
                        vesselAutoPilot.setTargetRoll(leadFlightTelemetry.getRoll());
                        // logger.info("lead roll {}", leadFlightTelemetry.getRoll());

                        vesselAutoPilot.setTargetHeading(leadFlightTelemetry.getHeading());
                        vesselAutoPilot.setTargetDirection(leadFlightTelemetry.getDirection());
                    }
                } catch(RPCException e){
                    e.printStackTrace();
                } catch(IllegalArgumentException e) {
                    e.printStackTrace();
                    vessels.remove(v);
                } catch (IllegalMonitorStateException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // set autopilot on all vessels other than the active vessel to target pitch 0
    public static void setAutopilotLevelOnSquadron(List<SpaceCenter.Vessel> vessels, SpaceCenter.Vessel leader) {
        try {
            logger.info("Squadron on full auto mode, will maintain pitch, roll, heading, and target direction");

            vessels.parallelStream().forEach(v -> {
                if(!v.equals(leader)) {
                    try {
                        SpaceCenter.AutoPilot vesselAutoPilot = v.getAutoPilot();
                        vesselAutoPilot.setTargetPitch(4);
                        vesselAutoPilot.setTargetRoll(0);
                        v.getControl().setThrottle(0.6F);
                    } catch (RPCException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setNonDirectionalControls(SpaceCenter.Control vesselControl,
                                   SpaceCenter.Control leadControl) {
        try {
            vesselControl.setBrakes(leadControl.getBrakes());
            // vesselControl.setSAS(leadControl.getSAS());
            vesselControl.setGear(leadControl.getGear());
            vesselControl.setThrottle(leadControl.getThrottle());
        } catch (RPCException e) {
            e.printStackTrace();
        }
    }

    private static void setActionGroupsOnSquadron(int groupNumber, boolean groupState, List<SpaceCenter.Vessel> squadronVessels) {

        for(SpaceCenter.Vessel vessel : squadronVessels) {
            try {
                vessel.getControl().setActionGroup(groupNumber, groupState);
            } catch (RPCException e) {
                e.printStackTrace();
            }
        }
    }

    private static void deployChutes(List<SpaceCenter.Vessel> vessels) {
            try {
                for(SpaceCenter.Vessel v: vessels ) {
                    SpaceCenter.Parts allParts = v.getParts();
                    List<SpaceCenter.Parachute> parachutes = allParts.getParachutes();
                    for (int i = 0; i < parachutes.size(); i++) {
                        logger.info("Deploying parachute {}", parachutes.get(i));
                        parachutes.get(i).deploy();
                    }
                }
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