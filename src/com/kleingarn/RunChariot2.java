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

public class RunChariot2 {

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
            spaceCenter.setActiveVessel(vessel);
            vessels.add(vessel);
        }

        SpaceCenter.Vessel leader = null;
        List<SpaceCenter.Vessel> potentialLeadList = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "longAntenna");
        for (SpaceCenter.Vessel vessel : potentialLeadList) {
            logger.info("Setting vessel {} name to ", vessel.getName(), leaderName);
            vessel.setName(leaderName);
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

        SpaceCenter.Control leadControl = leader.getControl();
        SpaceCenter.Flight leadFlightTelemetry = leader.flight(leader.getSurfaceReferenceFrame());

        logger.info("##### Built squadron from available active vessels #####");

        deployChutes(vessels);
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