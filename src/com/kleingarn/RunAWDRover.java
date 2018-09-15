package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class RunAWDRover {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    final static String squadronName = "wheel";
    final static String leaderName = "wheel_lead"; // Mark2Cockpit

    final static String leftWheelName = "wheel_left"; // ladder1

    final static String rightWheelName = "wheel_right"; // longAntenna

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("AWD Rover");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());


        SpaceCenter.Vessel lead = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "Mark2Cockpit").get(0);
        logger.info("Setting lead vessel {} name to {}", lead.getName(), leaderName);
        lead.setName(leaderName);

        List<SpaceCenter.Vessel> leftWheels = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "ladder1");
        for (SpaceCenter.Vessel vessel : leftWheels) {
            logger.info("Setting left wheel vessel {} name to {}", vessel.getName(), leftWheelName);
            vessel.setName(leftWheelName);
        }

        List<SpaceCenter.Vessel> rightWheels = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "longAntenna");
        for (SpaceCenter.Vessel vessel : rightWheels) {
            logger.info("Setting right wheel vessel {} name to {}", vessel.getName(), rightWheelName);
            vessel.setName(rightWheelName);
        }


//        List<SpaceCenter.Vessel> rightWheels = Squadron.getVesselsWithPart();

        Squadron squad = Squadron.buildSquadron(
                squadronName,
                leaderName,
                spaceCenter);

        int leadPollingIntervalMillis = 10;
        SpaceCenter.Vessel leader = squad.getSquadLeader();
        List<SpaceCenter.Vessel> vessels = squad.getSquadronVessels();
        SpaceCenter.Control leadControl = leader.getControl(); // initialized later in while loop

        logger.info("##### Built squadron from available active vessels #####");
        logger.info("squadron name: {}", squad.getSquadronName());
        logger.info("squad leader: {}", squad.getSquadLeader().getName());
        logger.info("squadron peeps: {}", squad.getSquadronVessels().stream().count());

        logger.info("Updating controls every {} ms", leadPollingIntervalMillis);

        logger.info("Locking brakes");
        for(SpaceCenter.Vessel vessel : vessels) {
            vessel.getControl().setBrakes(true);
        }

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
                    if (!v.equals(leader)) {
                        SpaceCenter.Control vesselControl = v.getControl();

                        vesselControl.setThrottle(throttle);
                        logger.info("Setting throttle to {}", throttle);
                        vesselControl.setGear(gear);

                        // 1 turn left
                        if(leftActionGroup) {
                            if(v.getName().equals(leftWheelName)) {
                                vesselControl.setRoll(-throttle);
                                logger.info("Left wheel, setting roll for vessel {} to 1", v.getName());
                            } else {
                                vesselControl.setRoll(-throttle);
                                logger.info("Not left wheel, setting roll for vessel {} to -1", v.getName());
                            }
                        } else if(rightActionGroup) {
                            if(v.getName().equals(rightWheelName)) {
                                vesselControl.setRoll(throttle);
                                logger.info("Right wheel, setting roll for vessel {} to 1", v.getName());
                            } else {
                                vesselControl.setRoll(throttle);
                                logger.info("Not left wheel, setting roll for vessel {} to -1", v.getName());
                            }
                        } else if(forwardActionGroup) {
                            if(v.getName().equals(leftWheelName)) {
                                vesselControl.setRoll(throttle);
                                logger.info("Right wheel, setting roll for vessel {} to 1", v.getName());
                            } else {
                                vesselControl.setRoll(-throttle);
                                logger.info("Not left wheel, setting roll for vessel {} to -1", v.getName());
                            }
                        } else if(reverseActionGroup) {
                            if(v.getName().equals(leftWheelName)) {
                                vesselControl.setRoll(-throttle);
                                logger.info("Left wheel, setting roll for vessel {} to -1", v.getName());
                            } else {
                                vesselControl.setRoll(throttle);
                                logger.info("Right wheel, setting roll for vessel {} to 1", v.getName());
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

    private static void sleep (int sleepTimeInmillis) {
        try {
            Thread.sleep(sleepTimeInmillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}