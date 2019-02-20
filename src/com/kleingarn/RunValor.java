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

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    final static String squadronName = "valor";
    final static String leaderName = "valor_lead"; // Mark2Cockpit
    final static String leftPropName = "valor_left_prop"; // longAntenna
    final static String rightPropName = "valor_right_prop"; // ladder1
    final static String centralPivotName = "valor_central_pivot"; // probeCoreCube

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Valor control mechanism");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        // valor_lead part mk3Cockpit.Shuttle
        SpaceCenter.Vessel lead = spaceCenter.getActiveVessel();
        logger.info("Setting lead vessel {} name to {}", lead.getName(), leaderName);
        lead.setName(leaderName);

        // decouple the props and rename them
        spaceCenter.getActiveVessel().getControl().activateNextStage();

        List<SpaceCenter.Vessel> leftProps = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "longAntenna");
        for (SpaceCenter.Vessel vessel : leftProps) {
            logger.info("Setting left prop vessel {} name to {}", vessel.getName(), leftPropName);
            vessel.setName(leftPropName);
        }

        List<SpaceCenter.Vessel> rightProps = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "ladder1");
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

            leadControl = leader.getControl();
            float throttle = leadControl.getThrottle();

            if (leadControl.getActionGroup(5)) {

                // detach next stage
                spaceCenter.getActiveVessel().getControl().activateNextStage();

                // identify probeCoreCube
                // part with sensorBarometer
                List<SpaceCenter.Vessel> centralPivotVessel = Squadron.getVesselsWithPart(spaceCenter.getVessels(), "sensorBarometer");
                for (SpaceCenter.Vessel vessel : centralPivotVessel) {
                    logger.info("Setting right prop vessel {} name to {}", vessel.getName(), centralPivotName);
                    vessel.setName(centralPivotName);
                }
                squad.addVesselToSquadron(centralPivotVessel.get(0));

                logger.info("##### Added central pivot to squadron #####");
                logger.info("squadron name: {}", squad.getSquadronName());
                logger.info("squad leader: {}", squad.getSquadLeader().getName());
                logger.info("squadron peeps: {}", squad.getSquadronVessels().stream().count());

                leadControl.setActionGroup(5, false);
            }

            squad.getSquadronVessels().parallelStream().forEach(v -> {

                try {

                    // move props
                    if (v.getName().equals(centralPivotName)) {
                        logger.info("Acting on central pivot");
                        if (leader.getControl().getActionGroup(1)) {
                            logger.info("HORIZONTAL");
                            v.getControl().setInputMode(SpaceCenter.ControlInputMode.OVERRIDE);
                            v.getControl().setPitch(-0.5F);
                            v.getControl().setSASMode(SpaceCenter.SASMode.STABILITY_ASSIST);
                            v.getControl().setSAS(true);
                            logger.info("current pitch: {}", v.flight(v.getSurfaceReferenceFrame()).getPitch());
                        } else if (leader.getControl().getActionGroup(2)) {
                            logger.info("VERTICAL");
//                            v.getControl().setInputMode(SpaceCenter.ControlInputMode.ADDITIVE);
//                            v.getControl().setPitch(0);
//                            v.getControl().setSASMode(SpaceCenter.SASMode.RADIAL);
//                            v.getControl().setSAS(true);
                            v.getControl().setInputMode(SpaceCenter.ControlInputMode.OVERRIDE);
                            v.getControl().setPitch(0.5F);
                            v.getControl().setSASMode(SpaceCenter.SASMode.STABILITY_ASSIST);
                            v.getControl().setSAS(true);
                            logger.info("current pitch: {}", v.flight(v.getSurfaceReferenceFrame()).getPitch());
                        } else {
                            logger.info("NO-OP");
                            v.getControl().setPitch(0);
                            v.getControl().setSASMode(SpaceCenter.SASMode.STABILITY_ASSIST);
                            v.getControl().setSAS(true);
                            logger.info("current pitch: {}", v.flight(v.getSurfaceReferenceFrame()).getPitch());
                        }

                        // need a way to set pitch 

                    }

                    // spin left prop
                    if(v.getName().equals(leftPropName)) {
                        v.getControl().setSAS(false);
                        SpaceCenter.Control vesselControl = v.getControl();
                        vesselControl = v.getControl();
                        vesselControl.setRoll(throttle);
                    }
                    // spin right prop
                    else if(v.getName().equals(rightPropName)) {
                        v.getControl().setSAS(false);
                        SpaceCenter.Control vesselControl = v.getControl();
                        vesselControl = v.getControl();
                        vesselControl.setRoll(-throttle);
                    }
                    logger.info("Controlling vessel {}, setting throttle on roll axis", v.getName());

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