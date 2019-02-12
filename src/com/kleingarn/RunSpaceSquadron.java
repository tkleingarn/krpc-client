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

public class RunSpaceSquadron {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    static String leaderName = "lead";
    final static String squadronName = "multi-launch";

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

        SpaceCenter.Vessel lead = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "longAntenna").get(0);
        logger.info("Setting lead vessel {} name to {}", lead.getName(), leaderName);
        lead.setName(leaderName);

        Squadron squad = Squadron.buildSquadron(
                squadronName,
                leaderName,
                spaceCenter);

        SpaceCenter.Vessel leader = squad.getSquadLeader();
        List<SpaceCenter.Vessel> vessels = squad.getSquadronVessels();
        for(SpaceCenter.Vessel v : vessels) {
            SpaceCenter.AutoPilot ap = v.getAutoPilot();
            ap.engage();
        }
        SpaceCenter.Control leadControl = leader.getControl();
        SpaceCenter.Flight leadFlightTelemetry = leader.flight(leader.getSurfaceReferenceFrame());

        logger.info("##### Built squadron from available active vessels #####");
        logger.info("squadron name: {}", squad.getSquadronName());
        logger.info("squad leader: {}", squad.getSquadLeader().getName());
        logger.info("squadron peeps: {}", squad.getSquadronVessels().stream().count());

        // v2
        // periodically get all config from leader and apply to squadron
        logger.info("Updating autopilot for squad every {} ms", leadPollingIntervalMillis);

        List<SpaceCenter.Decoupler> allDecouplers = spaceCenter.getActiveVessel().getParts().getDecouplers();
        logger.info("Current vessel has " + allDecouplers.size() + " decouplers");

        while (true) {

            leadControl = leader.getControl();
            if (leader.getControl().getActionGroup(7)) {
                vessels.add(leader);
                leadFlightTelemetry = leader.flight(leader.getSurfaceReferenceFrame());
                setAutopilotOrbitalTargets(vessels, leadFlightTelemetry);
            } else {
                vessels.remove(leader);
                setAutopilotTargets(squad, leader, leadFlightTelemetry, leadControl);
            }

            for (int i=0; i<10; i++) {
                setActionGroupsOnSquadron(i, leadControl.getActionGroup(i), vessels);
            }

            if (leader.getControl().getActionGroup(5)) {
                logger.info("Deploying even chutes");
                deployChutes(spaceCenter, leader, true);
                leadControl.setActionGroup(5, false);
            }

            if (leader.getControl().getActionGroup(6)) {
                logger.info("Deploying odd chutes");
                deployChutes(spaceCenter, leader, false);
                leadControl.setActionGroup(6, false);
            }

            activateStagesBasedOnLeader(spaceCenter, squad, lead);

            sleep(leadPollingIntervalMillis);
        }
    }

    public static void activateStagesBasedOnLeader(SpaceCenter spaceCenter, Squadron squad, SpaceCenter.Vessel leader) {

        for(SpaceCenter.Vessel v : squad.getSquadronVessels()) {
            try {
                SpaceCenter.Control leadControl = leader.getControl();
                SpaceCenter.Control vesselControl = v.getControl();

                // stage
                logger.info("{} current stage is {}", v.getName(), vesselControl.getCurrentStage());
                logger.info("lead current stage is {}", leadControl.getCurrentStage());

                if (vesselControl.getCurrentStage() > leadControl.getCurrentStage()) {
                    spaceCenter.setActiveVessel(v);
                    vesselControl.activateNextStage();
                    logger.info("Activating stage {} on vessel {}", vesselControl.getCurrentStage(), v.getName());
                }
            } catch (RPCException e) {
                e.printStackTrace();
            } catch (UnsupportedOperationException e) {
                e.printStackTrace();
            }
        }
        try {
            spaceCenter.setActiveVessel(leader);
        } catch (RPCException e) {
            e.printStackTrace();
        }
    }


    public static void setAutopilotTargets(Squadron squad,
                                           SpaceCenter.Vessel leader,
                                           SpaceCenter.Flight leadFlightTelemetry,
                                           SpaceCenter.Control leadControl) {
        try {
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
                            vesselAutoPilot.setRollThreshold(rollThreshold);
                        }

                        // set non-directional controls
                        setNonDirectionalControls(vesselControl, leadControl);

                        // set flight telemetry targets
                        vesselAutoPilot.setTargetPitch(leadFlightTelemetry.getPitch());
                        vesselAutoPilot.setRollThreshold(rollThreshold);
                        vesselAutoPilot.setTargetRoll(leadFlightTelemetry.getRoll());
                        vesselAutoPilot.setTargetHeading(leadFlightTelemetry.getHeading());
                        vesselAutoPilot.setTargetDirection(leadFlightTelemetry.getDirection());
                    }
                } catch (RPCException e) {
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    squad.removeVesselFromSquadron(v);
                } catch (IllegalMonitorStateException e) {
                    e.printStackTrace();
                }
            });
        } catch (IllegalArgumentException e) {
            logger.error("Vessel destroyed, removing from squadron");
            e.printStackTrace();
        }
    }

    // set autopilot on all vessels other than the active vessel to target pitch 0
    public static void setAutopilotOrbitalTargets(List<SpaceCenter.Vessel> vessels, SpaceCenter.Flight leadFlightTelemetry) {
        try {
            logger.info("Squadron on full auto mode, will maintain pitch, roll, heading, and target direction");
            vessels.parallelStream().forEach(v -> {
                try {

                    v.getControl().setThrottle(0.95F);
                    SpaceCenter.AutoPilot vesselAutoPilot = v.getAutoPilot();
                    vesselAutoPilot.setTargetRoll(0);
                    vesselAutoPilot.setTargetHeading(leadFlightTelemetry.getHeading());
                    vesselAutoPilot.setTargetDirection(leadFlightTelemetry.getDirection());

                    SpaceCenter.Flight vesselFlight = v.flight(v.getSurfaceReferenceFrame());
                    double surfaceAltitude = vesselFlight.getSurfaceAltitude();

                    if(surfaceAltitude < 10000) {
                        vesselAutoPilot.setTargetPitch(90);
                    } else if(surfaceAltitude >= 10000) {
                        vesselAutoPilot.setTargetPitch(70);
                    } else if(surfaceAltitude >= 20000) {
                        vesselAutoPilot.setTargetPitch(50);
                    } else if(surfaceAltitude >= 30000) {
                        vesselAutoPilot.setTargetPitch(25);
                    } else if(surfaceAltitude >= 40000) {
                        vesselAutoPilot.setTargetPitch(10);
                    } else if(surfaceAltitude >= 50000) {
                        vesselAutoPilot.setTargetPitch(-30);
                    } else if(surfaceAltitude >= 60000) {
                        vesselAutoPilot.setTargetPitch(-5);
                    } else if(surfaceAltitude >= 70000) {
                        vesselAutoPilot.setTargetPitch(0);
                    }
                } catch (RPCException e) {
                    e.printStackTrace();
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

    private static void deployChutes(SpaceCenter spaceCenter, SpaceCenter.Vessel vessel, boolean even) {
            try {
                spaceCenter.setActiveVessel(vessel);
                SpaceCenter.Parts allParts = vessel.getParts();

                List<SpaceCenter.Parachute> parachutes = allParts.getParachutes();
                for (int i = 0; i < parachutes.size(); i++) {
                    if(even && (i % 2 == 0)) {
                        logger.info("Deploying even parachute {}", parachutes.get(i));
                        parachutes.get(i).deploy();
                    } else if (!even && (i % 2 != 0)) {
                        logger.info("Deploying odd parachute {}", parachutes.get(i));
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