package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.Stream;
import krpc.client.StreamException;
import krpc.client.services.SpaceCenter;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class Squadron {

    private final static Logger logger = LoggerFactory.getLogger(Squadron.class);
    private static String squadronName;
    private static List<SpaceCenter.Vessel> squadronVessels;
    private static SpaceCenter.Vessel squadLeader;

    private final static float streamRate = 16.0f; // 0.16
                                                                               // pitch, roll, yaw
    private final static Triplet<Double, Double, Double> timeToPeak = new Triplet<>(0.1, 0.1, 0.1);
    private final static Triplet<Double, Double, Double> attenuationAngle = new Triplet<>(0.3, 1.8, 0.9);
    // perhaps experiment with overshoot


    public Squadron(String squadronName, List<SpaceCenter.Vessel> squadronVessels, SpaceCenter.Vessel squadLeader) {
        this.squadronName = squadronName;
        this.squadronVessels = squadronVessels;
        this.squadLeader = squadLeader;
    }

    public static Squadron buildSquadron(String vesselPrefix, String leaderVesselName, SpaceCenter spaceCenter) {

        try {
            // get active vessels
            List<SpaceCenter.Vessel> allVessels = spaceCenter.getVessels();
            logger.info("##### Listing all active vessels #####");
            printActiveVesselsFromList(allVessels);

            // find squadron
            List<SpaceCenter.Vessel> squadronVessels = allVessels.stream().filter(v -> {
                try {
                    return v.getName().contains(vesselPrefix);
                } catch (RPCException e) {
                    e.printStackTrace();
                }
                return false;}).collect(toList());
            logger.info("##### Listing vessels in squadron #####");
            printActiveVesselsFromList(squadronVessels);

            // find leader
            List<SpaceCenter.Vessel> squadLeaderList = squadronVessels.stream().filter(v -> {
                try {
                    return v.getName().contains(leaderVesselName);
                } catch (RPCException e) {
                    e.printStackTrace();
                }
                return false;}).collect(toList());
            logger.info("##### Listing squad leader #####");
            printActiveVesselsFromList(squadLeaderList);
            SpaceCenter.Vessel leadVessel = squadLeaderList.get(0);
            logger.info("Squad leader set = {} ", leadVessel.getName());

            return new Squadron(vesselPrefix, squadronVessels, leadVessel);
        } catch(RPCException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void printActiveVesselsFromList(List<SpaceCenter.Vessel> vessels) {
        int i = 0;
        for(SpaceCenter.Vessel vessel : vessels) {
            try {
                logger.info("Vessel found: {}", vessel.getName());
            } catch (RPCException e) {
                e.printStackTrace();
            }
            i++;
        }
        logger.info("Total vessels: {}", i);
    }

    public static String getSquadronName() {
        return squadronName;
    }
    public void setSquadronName(String squadronName) {
        this.squadronName = squadronName;
    }

    public static List<SpaceCenter.Vessel> getSquadronVessels() {
        return squadronVessels;
    }

    public void setSquadronVessels(List<SpaceCenter.Vessel> squadronVessels) {
        Squadron.squadronVessels = squadronVessels;
    }

    public SpaceCenter.Vessel getSquadLeader() {
        return squadLeader;
    }

    public void setSquadLeader(SpaceCenter.Vessel vessel) {
        Squadron.squadLeader = vessel;
    }

// saving as an example of using a callback
//
//        Stream<Boolean> brakes = connection.addStream(control, "getBrakes");
//        brakes.addCallback(
//                (Boolean x) -> {
//                    logger.info("Brakes set to {}" + x);
//                    // could set brake control here but let's be cool
//                    // each callback will need to update the config and apply it
//                    config.setBrakes(x);
//                    setUpdatesFromLeader(spaceCenter, connection, config);
//                });
//        brakes.start();

    public void setUpdatesFromLeader(SpaceCenter spaceCenter, Connection connection) {

        // set all values in the config
        for(SpaceCenter.Vessel vessel : getSquadronVessels()) {
            try {
                if (!vessel.equals(getSquadLeader())) {

                    SpaceCenter.Control control = vessel.getControl();
                    SpaceCenter.AutoPilot ap = vessel.getAutoPilot();
                    // ap.setTimeToPeak(new Triplet<>(0.25, 1.0, 0.25));
                    ap.setTimeToPeak(timeToPeak);

                    logger.info("AutoPilot time to peak = {}", ap.getTimeToPeak());
                    // attenuation angle is pitch, roll, yaw
                    logger.info("AutoPilot attenuation angle is = {}", ap.getAttenuationAngle());
                    // ap.setAttenuationAngle(new Triplet<>(0.25, 2.0, 0.25));
                    ap.setAttenuationAngle(attenuationAngle);
                    ap.engage();

//                    vessel.getAutoPilot().setTargetDirection(config.getDirection());
//                    logger.info("setting direction to {}", config.getDirection());
                }
            } catch (RPCException e) {
                e.printStackTrace();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
            }
            // TODO: consider adding a IllegalArgumentException here when a vessel dies
        }
    }
}