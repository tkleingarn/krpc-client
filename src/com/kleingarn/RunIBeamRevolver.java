package com.kleingarn;

import com.kleingarn.Squadron;
import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class RunIBeamRevolver {

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

        // Parts:
        // Mark2Cockpit
        // ladder1
        // longAntenna

        SpaceCenter.Vessel lead = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "ladder1").get(0);
        logger.info("Setting lead vessel {} name to {}", lead.getName(), leaderName);
        lead.setName(leaderName);

        List<SpaceCenter.Vessel> leftGuns = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "longAntenna");
        for (SpaceCenter.Vessel vessel : leftGuns) {
            logger.info("Setting left gun vessel {} name to {}", vessel.getName(), leftGunName);
            vessel.setName(leftGunName);
        }

//        List<SpaceCenter.Vessel> rightGuns = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "longAntenna");
//        for (SpaceCenter.Vessel vessel : rightGuns) {
//            logger.info("Setting right gun vessel {} name to {}", vessel.getName(), rightGunName);
//            vessel.setName(rightGunName);
//        }


//        List<SpaceCenter.Vessel> rightWheels = Squadron.getVesselsWithPart();

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
            float throttle = leadControl.getThrottle();

            logger.info("Action group state is 1: " + leadControl.getActionGroup(1)
                    + ", 2 is: " + leadControl.getActionGroup(2)
                    + ", 3 is: " + leadControl.getActionGroup(3)
            );

            squad.getSquadronVessels().parallelStream().forEach(v -> {
                try {
                    if (!v.equals(leader)) {
                        SpaceCenter.Control vesselControl = v.getControl();

//                        vesselControl.setThrottle(throttle);
//                        logger.info("Setting throttle to {}", throttle);
//                        vesselControl.setGear(gear);

                        // 1 = rotate barrel
                        // if iBeam is being heated, decouple
                        if(fireActionGroup) {
                            if(v.getName().equals(leftGuns)) {
                                vesselControl.setRoll(-throttle);
                                logger.info("Left gun, setting roll for vessel {} to 1", v.getName());
                            } else {
                                vesselControl.setRoll(-throttle);
                                logger.info("Not left gun, setting roll for vessel {} to -1", v.getName());
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