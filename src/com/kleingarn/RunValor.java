package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class RunValor {

    final static Logger logger = LoggerFactory.getLogger(RunValor.class);

    final static String squadronName = "valor";
    final static String leaderName = "valor_lead"; // Mark2Cockpit

    final static String leftPropName = "left_prop"; // longAntenna

    final static String rightPropName = "right_prop"; // ladder1

    final static String centralPivotName = "central_pivot"; // TBD cube sat part name

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Valor control mechanism");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());


        SpaceCenter.Vessel lead = spaceCenter.getActiveVessel();
        logger.info("Setting lead vessel {} name to {}", lead.getName(), leaderName);
        lead.setName(leaderName);

        // decouple the props and rename them
        spaceCenter.getActiveVessel().getControl().activateNextStage();

        List<SpaceCenter.Vessel> leftProps = Squadron.getVesselsWithPart(spaceCenter.getVessels(), leftPropName);
        for (SpaceCenter.Vessel vessel : leftProps) {
            logger.info("Setting left prop vessel {} name to {}", vessel.getName(), leftPropName);
            vessel.setName(leftPropName);
        }

        List<SpaceCenter.Vessel> rightProps = Squadron.getVesselsWithPart(spaceCenter.getVessels(), rightPropName);
        for (SpaceCenter.Vessel vessel : rightProps) {
            logger.info("Setting right prop vessel {} name to {}", vessel.getName(), rightPropName);
            vessel.setName(rightPropName);
        }
        logger.info("Lead and props identified.");
        spaceCenter.setActiveVessel(lead);

        Squadron squad = Squadron.buildSquadron(
                squadronName,
                leaderName,
                spaceCenter);

        int leadPollingIntervalMillis = 500;
        SpaceCenter.Vessel leader = squad.getSquadLeader();
        List<SpaceCenter.Vessel> vessels = squad.getSquadronVessels();
        SpaceCenter.Control leadControl = leader.getControl(); // initialized later in while loop

        logger.info("##### Built squadron from available active vessels #####");
        logger.info("squadron name: {}", squad.getSquadronName());
        logger.info("squad leader: {}", squad.getSquadLeader().getName());
        logger.info("squadron peeps: {}", squad.getSquadronVessels().stream().count());

        logger.info("Updating controls every {} ms", leadPollingIntervalMillis);

        while (true) {

            boolean leftActionGroup = leadControl.getActionGroup(1);
            boolean forwardActionGroup = leadControl.getActionGroup(2);
            boolean rightActionGroup = leadControl.getActionGroup(3);
            boolean reverseActionGroup = leadControl.getActionGroup(4);
            boolean gear = !leadControl.getActionGroup(5);
            float throttle = leadControl.getThrottle();

            logger.info("Action group state is 1: " + leadControl.getActionGroup(1)
                    + ", 2 is: " + leadControl.getActionGroup(2)
                    + ", 3 is: " + leadControl.getActionGroup(3)
            );

            squad.getSquadronVessels().parallelStream().forEach(v -> {
                try {
                    // spin left prop
                    if(v.getName().equals(leftPropName)) {
                        SpaceCenter.Control vesselControl = v.getControl();
                        vesselControl.setRoll(-throttle);
                    }
                    // spin right prop
                    else if(v.getName().equals(rightPropName)) {
                        SpaceCenter.Control vesselControl = v.getControl();
                        vesselControl.setRoll(throttle);
                    }

//                    if (!v.equals(leader)) {
//                        SpaceCenter.Control vesselControl = v.getControl();
//
//                        vesselControl.setThrottle(throttle);
//                        logger.info("Setting throttle to {}", throttle);
//                        vesselControl.setGear(gear);
//
//                        // 1 turn left
//                        if(leftActionGroup) {
//                            if(v.getName().equals(leftPropName)) {
//                                vesselControl.setRoll(-throttle);
//                                logger.info("Left wheel, setting roll for vessel {} to 1", v.getName());
//                            } else {
//                                vesselControl.setRoll(-throttle);
//                                logger.info("Not left wheel, setting roll for vessel {} to -1", v.getName());
//                            }
//                        } else if(rightActionGroup) {
//                            if(v.getName().equals(rightPropName)) {
//                                vesselControl.setRoll(throttle);
//                                logger.info("Right wheel, setting roll for vessel {} to 1", v.getName());
//                            } else {
//                                vesselControl.setRoll(throttle);
//                                logger.info("Not left wheel, setting roll for vessel {} to -1", v.getName());
//                            }
//                        } else if(forwardActionGroup) {
//                            if(v.getName().equals(leftPropName)) {
//                                vesselControl.setRoll(throttle);
//                                logger.info("Right wheel, setting roll for vessel {} to 1", v.getName());
//                            } else {
//                                vesselControl.setRoll(-throttle);
//                                logger.info("Not left wheel, setting roll for vessel {} to -1", v.getName());
//                            }
//                        } else if(reverseActionGroup) {
//                            if(v.getName().equals(leftPropName)) {
//                                vesselControl.setRoll(-throttle);
//                                logger.info("Left wheel, setting roll for vessel {} to -1", v.getName());
//                            } else {
//                                vesselControl.setRoll(throttle);
//                                logger.info("Right wheel, setting roll for vessel {} to 1", v.getName());
//                            }
//                        } else v.getControl().setRoll(0);
//                    }
                } catch(RPCException e){
                    e.printStackTrace();
                }
            });

            sleep(leadPollingIntervalMillis);
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