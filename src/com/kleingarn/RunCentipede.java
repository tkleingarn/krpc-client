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

public class RunCentipede {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    static String leaderName = "puller";
    final static String squadronName = "centipede";

    static SpaceCenter.Vessel leadVessel;

    // The threshold at which the autopilot will try to match the target roll angle, if any. Defaults to 5 degrees.
    final static double rollThreshold = 5.0;

    final static int leadPollingIntervalMillis = 1000;

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Squadron flight");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

//        List<SpaceCenter.Vessel> vessels = new ArrayList<>();

//        List<SpaceCenter.Vessel> potentialLeaderList = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "sensorBarometer");
//        for (SpaceCenter.Vessel vessel : potentialLeaderList) {
//            logger.info("Setting vessel {} name to ", vessel.getName(), leaderName);
//            vessel.setName(leaderName);
//            spaceCenter.setActiveVessel(vessel);
//            leadVessel = vessel;
//            vessels.add(vessel);
//        }

        leadVessel = spaceCenter.getActiveVessel();

        Squadron squad = Squadron.buildSquadron(
                squadronName,
                leaderName,
                spaceCenter);

        while (true) {
            for(SpaceCenter.Vessel vessel : squad.getSquadronVessels()) {
                try {
                    if(!vessel.equals(leadVessel)){
                        setNonDirectionalControls(vessel.getControl(), leadVessel.getControl());
                    }
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    logger.error("[ERROR] No such vessel, removing from squadron");
                    squad.removeVesselFromSquadron(vessel);
                }
            }

            if(leadVessel.getControl().getActionGroup(2)) {
                logger.info("Action group 1 is {}, decoupling all decouplers", leadVessel.getControl().getActionGroup(2));
                leadVessel.getControl().setActionGroup(2, false);
                for(SpaceCenter.Vessel vessel : squad.getSquadronVessels()) {
                    List<SpaceCenter.Decoupler> allDecouplers = vessel.getParts().getDecouplers();
                    for(SpaceCenter.Decoupler decoupler : allDecouplers) {
                        decoupler.decouple();
                    }
                }
            }

            sleep(leadPollingIntervalMillis);
        }
//        SpaceCenter.Control leadControl = leader.getControl();
//        SpaceCenter.Flight leadFlightTelemetry = leader.flight(leader.getSurfaceReferenceFrame());
//        deployChutes(vessels);
    }

    public static void setNonDirectionalControls(SpaceCenter.Control vesselControl,
                                                 SpaceCenter.Control leadControl) {
        try {
            vesselControl.setBrakes(leadControl.getBrakes());
            vesselControl.setSAS(leadControl.getSAS());
            vesselControl.setSASMode(leadControl.getSASMode());
            vesselControl.setGear(leadControl.getGear());
            vesselControl.setThrottle(leadControl.getThrottle());

//            vesselControl.setInputMode(SpaceCenter.ControlInputMode.OVERRIDE);
//            vesselControl.setPitch(leadControl.getPitch());
//            vesselControl.setWheelThrottle(leadControl.getWheelThrottle());
            vesselControl.setWheelThrottle(leadControl.getThrottle());
//            vesselControl.setInputMode(SpaceCenter.ControlInputMode.ADDITIVE);
//            vesselControl.setYaw(leadControl.getYaw());
//            vesselControl.setRoll(leadControl.getRoll());

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