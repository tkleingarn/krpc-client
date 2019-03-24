package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.print.Doc;
import java.io.IOException;
import java.util.List;

public class RunParadrop {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Paradrop");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        SpaceCenter.Vessel vessel = spaceCenter.getActiveVessel();

        /*
        [main] INFO com.kleingarn.DockingUtils - Part name: kerbalEVA, Stage: 0
        [main] INFO com.kleingarn.DockingUtils - Part name: kerbalEVAfemale, Stage: 1
        [main] INFO com.kleingarn.DockingUtils - Part name: kerbalEVA, Stage: 2
        [main] INFO com.kleingarn.DockingUtils - Part name: kerbalEVAfemale, Stage: 3
         */


        while(true) {
            if (vessel.getControl().getActionGroup(5)) {

                int currentStage = vessel.getControl().getCurrentStage();
                logger.info("Current stage is {}", currentStage);

                // get parts in stage

                SpaceCenter.Parts allParts = vessel.getParts();
                DockingUtils.printParts(allParts.getAll());
                List<SpaceCenter.Part> partsInCurrentStage = allParts.inStage(currentStage - 1);

                for(SpaceCenter.Part p : partsInCurrentStage) {
                    logger.info("Part in current stage is {}", p.getName());
//                    [main] INFO com.kleingarn.Squadron - Part in current stage is Separator.0
//                            [main] INFO com.kleingarn.Squadron - Part in current stage is kerbalEVAfemale
                    if(p.getName().contains("kerbalEVA")) {
                        logger.info("In current stage for {}", p.getName());

                        List<SpaceCenter.Parachute> parachutes = allParts.getParachutes();

                        for (SpaceCenter.Parachute parachute : parachutes) {
                            if(parachute.getPart().getStage() == currentStage) {
                                logger.info("Deploying parachute in stage");
                                parachute.deploy();
                                vessel.getControl().activateNextStage();
                            }
                        }
//                        List<SpaceCenter.Part> partsOnKerbal = p.getChildren();
//                        for(SpaceCenter.Part kerbalParts : partsOnKerbal) {
//                            p.getParachute().deploy();
//                        }
                    }
                }


                // deploy chutes in next stage

                // activate stage



//                List<SpaceCenter.Parachute> parachutes = vessel.getParts().getParachutes();
//                for(SpaceCenter.Parachute chute : parachutes) {
//                    logger.info("Parachute state is {}", chute.getState());
//
//                    if(chute.getState().equals(SpaceCenter.ParachuteState.STOWED) ||
//                            chute.getState().equals(SpaceCenter.ParachuteState.ARMED)) {
//                        logger.info("Deploying chute");
//                        chute.deploy();
//                    }
//                }


//                vessel.getControl().getCurrentStage();
//                List<SpaceCenter.Decoupler> allDecouplers = vessel.getParts().getDecouplers();
//                logger.info("Current vessel " + vessel.getName() + " has " + allDecouplers.size() + " decouplers");
//
//                int totalDecouplerCount = allDecouplers.size();
//                for (SpaceCenter.Decoupler decoupler : allDecouplers) {
//                    logger.info("Vessel {}, decoupler {}, stage {}, target stage {}", vessel.getName(), decoupler, decoupler.getPart().getDecoupleStage(), currentStage - 1);
//                    if (decoupler.getPart().getDecoupleStage() == (currentStage - 1)) {
//                        logger.info("Decoupling {} on vessel {}", decoupler.getPart(), vessel.getName());
//                        if (!decoupler.getDecoupled()) {
//                            List<SpaceCenter.Part> childPartsOnDecoupler = decoupler.getPart().getChildren();
//                            for(SpaceCenter.Part p : childPartsOnDecoupler) {
//                                logger.info("Child part on decoupler is {}", p.getName());
//                                // open chute
//                            }
//                            try {
//                                decoupler.decouple();
//                            } catch (UnsupportedOperationException e) {
//                                e.printStackTrace();
//                            }
//
//                            totalDecouplerCount--;
//                            currentStage--;
//                            sleep(1000);
//                        }
//                    }
//                }
            }
            sleep(1000);
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