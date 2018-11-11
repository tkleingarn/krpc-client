package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

         setting up Kerbals in decouplers differentiates the stages
         kerbalEVAVintage is a vintage suit
         kerbalEVAfemale is a female kerbal, why differentiate based on gender?
         [main] INFO com.kleingarn.DockingUtils - Part name: kerbalEVA, Stage: 2
         [main] INFO com.kleingarn.DockingUtils - Part name: kerbalEVAVintage, Stage: 2
         */

        int currentStage = vessel.getControl().getCurrentStage();
        SpaceCenter.Parts allParts = vessel.getParts();
        DockingUtils.printParts(allParts.getAll());

        while(true) {
            if (vessel.getControl().getActionGroup(5)) {

                logger.info("Proceeding with paradrop");
                for(int stage = currentStage; stage > -1; stage--) {
                    int nextStage = stage - 1;
                    logger.info("stage: " + stage + ", next stage: " + nextStage);
                    List<SpaceCenter.Part> partsInNextStage = allParts.inStage(nextStage);

                    for (SpaceCenter.Part p : partsInNextStage) {
                        logger.info("Part in stage " + (nextStage) + " is " + p.getName());
                        if (p.getName().contains("kerbalEVA")) {
                            logger.info("Deploying parachute for Kerbal in next stage {}", p.getName());
                            p.getParachute().deploy();
                        }
                    }

                    sleep(3000);
                    for (SpaceCenter.Part p : partsInNextStage) {
                        logger.info("Part in stage " + (nextStage) + " is " + p.getName());
                        if (p.getName().equals("smallRadialEngine")) {
                            logger.info("Activating engine, parachutes have been deployed");
                            p.getEngine().setActive(true);
                        }
                    }
                }

            } // end of action group detection while loop
            sleep(1000);
        }
    }

    private static void sleep(int sleepTimeInmillis) {
        try {
            Thread.sleep(sleepTimeInmillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}