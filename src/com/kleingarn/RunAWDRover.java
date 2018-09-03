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

public class RunAWDRover {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    final static String squadronName = "wheel";
    final static String leaderName = "wheel_lead";
    final static String leftWheels = "wheel_left";
    final static String rightWheels = "wheel_right";

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("AWD Rover");
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
        SpaceCenter.Control leadControl = leader.getControl(); // initialized later in while loop
        SpaceCenter.Flight leadFlightTelemetry = leader.flight(leader.getSurfaceReferenceFrame());

        logger.info("##### Built squadron from available active vessels #####");
        logger.info("squadron name: {}", squad.getSquadronName());
        logger.info("squad leader: {}", squad.getSquadLeader().getName());
        logger.info("squadron peeps: {}", squad.getSquadronVessels().stream().count());

        // periodically get all config from leader and apply to squadron
        logger.info("Updating autopilot for squad every {} ms", leadPollingIntervalMillis);

        while (true) {

            boolean leftActionGroup = leadControl.getActionGroup(1);
            boolean forwardActionGroup = leadControl.getActionGroup(2);
            boolean rightActionGroup = leadControl.getActionGroup(3);

            logger.info("Action group state is 1: " + leadControl.getActionGroup(1)
                    + ", 2 is: " + leadControl.getActionGroup(2)
                    + ", 3 is: " + leadControl.getActionGroup(3)
            );

            squad.getSquadronVessels().parallelStream().forEach(v -> {
                try {
                    if (!v.equals(leader)) {
                        SpaceCenter.Control vesselControl = v.getControl();
                        // 1 turn left
                        if(leftActionGroup) {
                            if(v.getName().equals(leftWheels)) {
                                vesselControl.setRoll(-1);
                                logger.info("Left wheel, setting roll for vessel {} to 1", v.getName());
                            } else {
                                vesselControl.setRoll(-1);
                                logger.info("Not left wheel, setting roll for vessel {} to -1", v.getName());
                            }
                        } else if(rightActionGroup) {
                            if(v.getName().equals(rightWheels)) {
                                vesselControl.setRoll(1);
                                logger.info("Right wheel, setting roll for vessel {} to 1", v.getName());
                            } else {
                                vesselControl.setRoll(1);
                                logger.info("Not left wheel, setting roll for vessel {} to -1", v.getName());
                            }
                        } else if(forwardActionGroup) {
                            if(v.getName().equals(leftWheels)) {
                                vesselControl.setRoll(1);
                                logger.info("Right wheel, setting roll for vessel {} to 1", v.getName());
                            } else {
                                vesselControl.setRoll(-1);
                                logger.info("Not left wheel, setting roll for vessel {} to -1", v.getName());
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
//    private static void setWheelsForward (SpaceCenter.Vessel vessel, SpaceCenter.Control vesselControl, int roll) {
//        try {
//                vesselControl.setRoll(1);
//                logger.info("Left wheel, setting roll for vessel {} to 1", v.getName());
//            }
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        } catch (RPCException e) {
//            e.printStackTrace();
//        }
//    }

    private static void sleep (int sleepTimeInmillis) {
        try {
            Thread.sleep(sleepTimeInmillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}