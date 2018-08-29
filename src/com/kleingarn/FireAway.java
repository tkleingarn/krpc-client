package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class FireAway {

    final static Logger logger = LoggerFactory.getLogger(RunClawVTOL.class);
    final static String craftName = "bomber 02";

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Flight");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        // assume we are flying already
        SpaceCenter.Vessel vessel = spaceCenter.getActiveVessel();
        SpaceCenter.Control vesselControl = vessel.getControl();

        while(true) {
            boolean fire = vesselControl.getActionGroup(1);
            if (fire) {
                List<SpaceCenter.Decoupler> allDecouplers = vessel.getParts().getDecouplers();
                for (int i = 0; i < allDecouplers.size(); i++) {
                    vesselControl.activateNextStage();
                    sleep(10);
                }
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