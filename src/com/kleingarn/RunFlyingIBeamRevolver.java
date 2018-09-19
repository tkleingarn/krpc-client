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

public class RunFlyingIBeamRevolver {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    final static String squadronName = "revolver";
    final static String leaderName = "revolver_lead"; // Mark2Cockpit
    final static String leftGunName = "revolver_left"; // ladder1
    final static String rightGunName = "revolver_right"; // longAntenna

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("i-Beam Revolver");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        // Parts used as identifiers:
        // Mark2Cockpit
        // mk2Cockpit.Inline
        // ladder1
        // longAntenna

//        SpaceCenter.Vessel lead = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "mk2Cockpit.Inline").get(0);
//        logger.info("Setting lead vessel {} name to {}", lead.getName(), leaderName);
//        lead.setName(leaderName);

        List<SpaceCenter.Vessel> leadVessels = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "mk2Cockpit.Inline");
        for (SpaceCenter.Vessel vessel : leadVessels) {
            logger.info("Setting lead vessel {} name to {}", vessel.getName(), leaderName);
            vessel.setName(leaderName);
            // there can be only one
            break;
        }

        List<SpaceCenter.Vessel> leftGuns = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "ladder1");
        for (SpaceCenter.Vessel vessel : leftGuns) {
            logger.info("Setting left gun vessel {} name to {}", vessel.getName(), leftGunName);
            vessel.setName(leftGunName);
        }

        List<SpaceCenter.Vessel> rightGuns = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "longAntenna");
        for (SpaceCenter.Vessel vessel : rightGuns) {
            logger.info("Setting right gun vessel {} name to {}", vessel.getName(), rightGunName);
            vessel.setName(rightGunName);
        }

        Squadron squad = Squadron.buildSquadron(
                squadronName,
                leaderName,
                spaceCenter);

        int leadPollingIntervalMillis = 1000;
        SpaceCenter.Vessel leader = squad.getSquadLeader();
        List<SpaceCenter.Vessel> vessels = squad.getSquadronVessels();
        SpaceCenter.Control leadControl = leader.getControl(); // initialized later in while loop

        logger.info("##### Built squadron from available active vessels #####");
        logger.info("squadron name: {}", squad.getSquadronName());
        logger.info("squad leader: {}", squad.getSquadLeader().getName());
        logger.info("squadron peeps: {}", squad.getSquadronVessels().stream().count());

        logger.info("Updating controls every {} ms", leadPollingIntervalMillis);


        while (true) {

            boolean fireActionGroup = leadControl.getActionGroup(1);
//            boolean forwardActionGroup = leadControl.getActionGroup(2);
//            boolean rightActionGroup = leadControl.getActionGroup(3);
//            boolean reverseActionGroup = leadControl.getActionGroup(4);
//            boolean gear = !leadControl.getActionGroup(5);
//            float throttle = leadControl.getThrottle();

            logger.info("Action group state is 1: " + leadControl.getActionGroup(1)
                    + ", 2 is: " + leadControl.getActionGroup(2)
                    + ", 3 is: " + leadControl.getActionGroup(3)
            );


            final float barrelRoll = 0.50F;
            final int tempThreshold = 7000;
            final Triplet<Double, Double, Double> customHighlightColor = new Triplet<>(1.0,0.00,0.00);

            squad.getSquadronVessels().parallelStream().forEach(v -> {
                try {
                    if (!v.equals(leader)) {
                        SpaceCenter.Control vesselControl = v.getControl();

                        // 1 = rotate barrel
                        if(fireActionGroup) {

                            // if iBeam is being heated, decouple
                            List<SpaceCenter.Decoupler> allDecouplers = v.getParts().getDecouplers();
                            logger.info("Current vessel " + v.getName() + " has " + allDecouplers.size() + " decouplers");

                            int totalDecouplerCount = allDecouplers.size();
                            while(totalDecouplerCount > 1) {
                                logger.info("Vessel {} has {} decouplers", v.getName(), totalDecouplerCount);
                                for (SpaceCenter.Decoupler decoupler : allDecouplers) {
                                    List<SpaceCenter.Part> children = decoupler.getPart().getChildren();
                                    for (SpaceCenter.Part childPart : children) {

                                        int multiplier = 10000;
//                                        logger.info("child part {} thermal conduction: {}, convection: {}, radiation {}, skinToInt {}",
//                                                childPart.getName(),
//                                                childPart.getThermalConductionFlux() * multiplier,
//                                                childPart.getThermalConvectionFlux() * multiplier,
//                                                childPart.getThermalRadiationFlux() * multiplier,
//                                                childPart.getThermalSkinToInternalFlux() * multiplier); // from the surrounding atmosphere

                                        // negative value of skinToInt indicates the skin is heating up
                                        // child part structuralIBeam2 thermal conduction: 53.19811, convection: -1260.2046, radiation -12968.449, skinToInt 12332.819
                                        if(childPart.getThermalSkinToInternalFlux() * multiplier > tempThreshold) {
                                            logger.info("Decoupling {} skinToInt is {}", decoupler.getPart().getName(), childPart.getThermalSkinToInternalFlux() * multiplier);
                                            try {
                                                childPart.setHighlighted(true);
                                                childPart.setHighlightColor(customHighlightColor);
                                                decoupler.decouple();
                                                totalDecouplerCount--;
                                                if(v.getName().equals(leftGunName)) {
                                                    vesselControl.setRoll(barrelRoll);
                                                    logger.info("Left gun, setting roll for vessel {}", v.getName());
                                                } else if(v.getName().equals(rightGunName)) {
                                                    vesselControl.setRoll(-barrelRoll);
                                                    logger.info("Right gun, setting roll for vessel {}", v.getName());
                                                }
                                                sleep(50);
                                                // decoupleEmpty(v, decoupler);
                                                decoupler.decouple();
                                            } catch (UnsupportedOperationException e) {
                                                logger.error("Error while decoupling");
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }
                            }

                        } else v.getControl().setRoll(0);
                    }
                } catch(RPCException e){
                    e.printStackTrace();
                }
            });

            sleep(leadPollingIntervalMillis);
        }
    }

    private static void rotateBarrel(SpaceCenter.Vessel v, float barrelRoll) {
        try {
            SpaceCenter.Control vesselControl = v.getControl();
                vesselControl.setRoll(barrelRoll);
                logger.info("Setting roll for vessel {} to 1", v.getName());
        } catch (RPCException e) {
            e.printStackTrace();
        }
    }


    private static void decoupleEmpty(SpaceCenter.Vessel v, SpaceCenter.Decoupler decoupler) {
        List<SpaceCenter.Part> children = null;
        try {
            children = decoupler.getPart().getChildren();
        } catch (RPCException e) {
            e.printStackTrace();
        }
        if(children.size() == 0) {
            logger.warn("Decoupling empty decoupler.");
            try {
                try {
                    decoupler.decouple();
                } catch (RPCException e) {
                    e.printStackTrace();
                }
            } catch (UnsupportedOperationException e) {
                logger.error("Error while decoupling");
                e.printStackTrace();
            }
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