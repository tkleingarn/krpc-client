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

public class RunFanManSquadron {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    static String leaderName = "triplane";
    final static String squadronName = "triplane";

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

        int leadPollingIntervalMillis = 1000;
        SpaceCenter.Vessel leader = squad.getSquadLeader();
        List<SpaceCenter.Vessel> vessels = squad.getSquadronVessels();
        SpaceCenter.Control leadControl = leader.getControl();
        SpaceCenter.Flight leadFlightTelemetry = leader.flight(leader.getSurfaceReferenceFrame());

        logger.info("##### Built squadron from available active vessels #####");
        logger.info("squadron name: {}", squad.getSquadronName());
        logger.info("squad leader: {}", squad.getSquadLeader().getName());
        logger.info("squadron peeps: {}", squad.getSquadronVessels().stream().count());

        // v2
        // periodically get all config from leader and apply to squadron
        logger.info("Updating autopilot for squad every {} ms", leadPollingIntervalMillis);
        boolean chutesDeployed = false;

        while (true) {

            leadControl = leader.getControl();
            leadFlightTelemetry = leader.flight(leader.getSurfaceReferenceFrame());
            setAutopilotTargets(squad, leader, leadFlightTelemetry, leadControl);

            if (leader.getControl().getActionGroup(5) && chutesDeployed != true) {
                logger.info("Deploying chutes on all Kerbals");
                for (SpaceCenter.Vessel v : vessels) {
                    deployChutes(spaceCenter, v);
                }
                chutesDeployed = true;
            }





            sleep(leadPollingIntervalMillis);
        }
    }

    public static void setAutopilotTargets(Squadron squad,
                                           SpaceCenter.Vessel leader,
                                           SpaceCenter.Flight leadFlightTelemetry,
                                           SpaceCenter.Control leadControl) {
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

    private static void deployChutes(SpaceCenter spaceCenter, SpaceCenter.Vessel vessel) {
            try {
                spaceCenter.setActiveVessel(vessel);
                SpaceCenter.Parts allParts = vessel.getParts();
                List<SpaceCenter.Parachute> parachutes = allParts.getParachutes();
                for (SpaceCenter.Parachute parachute : parachutes) {
                    parachute.deploy();
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