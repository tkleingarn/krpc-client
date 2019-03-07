package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class RunFanManDualProp {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    final static String squadronName = "fan man";
    final static String leaderName = "fan man lead"; // Mark2Cockpit
    final static String leftPropName = "fan man left prop"; // longAntenna
    final static String rightPropName = "fan man right prop"; // ladder1
    final static String centralPivotName = "fan man central pivot"; // probeCoreCube

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

            try {
                squad.getSquadronVessels().parallelStream().forEach(v -> {

                    try {

                        v.getControl().setActionGroup(6, leader.getControl().getActionGroup(6));

                        // spin left prop
                        if (v.getName().equals(leftPropName)) {
                            v.getControl().setSAS(false);
                            SpaceCenter.Control vesselControl = v.getControl();
                            vesselControl.setRoll(throttle);
                        }
                        // spin right prop
                        else if (v.getName().equals(rightPropName)) {
                            v.getControl().setSAS(false);
                            SpaceCenter.Control vesselControl = v.getControl();
                            vesselControl.setRoll(-throttle);
                        }
                        logger.info("Controlling vessel {}, setting throttle on roll axis", v.getName());

                    } catch (RPCException e) {
                        e.printStackTrace();
                    }
                });

                // TODO: (tkleingarn) add centralPivot back if undocked


            } catch (IllegalArgumentException e) {
                logger.error("[ERROR] Probably lost a vessel");
                e.printStackTrace();
                squad = removeCentralPivotIfDockingPortsDocked(lead, squad, spaceCenter);
            }
            sleep(leadPollingIntervalMillis);
        }
    }

    private static Squadron removeCentralPivotIfDockingPortsDocked(SpaceCenter.Vessel vessel, Squadron squadron, SpaceCenter spaceCenter) {
        try {
            List<SpaceCenter.DockingPort> dockingPorts = vessel.getParts().getDockingPorts();
            for(SpaceCenter.DockingPort port : dockingPorts) {
                if(port.getState().equals(SpaceCenter.DockingPortState.DOCKED)) {
//                    for (SpaceCenter.Vessel v : squadron.getSquadronVessels()) {
//                        if (v.getName().equals(centralPivotName)) {
//                            squadron.removeVesselFromSquadron(v);
//                            logger.warn("[WARN] Removing vessel from squadron");
//                        }
//                    }
                    squadron = Squadron.buildSquadron(
                            squadronName,
                            leaderName,
                            spaceCenter);

                    logger.warn("[WARN] Reset squadron");
                    return squadron;
                }
            }
        } catch (RPCException e) {
            e.printStackTrace();
        }
        logger.info("[INFO] No changes to squadron");
        return squadron;
    }

    private static void sleep (int sleepTimeInmillis) {
        try {
            Thread.sleep(sleepTimeInmillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}