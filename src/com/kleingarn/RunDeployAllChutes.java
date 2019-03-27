package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RunDeployAllChutes {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Squadron flight");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        SpaceCenter.Parts parts = spaceCenter.getActiveVessel().getParts();
        deployChutes(parts);
    }

    private static void deployChutes(SpaceCenter.Parts parts) {
        try {
            List<SpaceCenter.Parachute> parachutes = parts.getParachutes();
            for (int i = 0; i < parachutes.size(); i++) {
                logger.info("Deploying parachute {}", parachutes.get(i));
                parachutes.get(i).deploy();
            }
        } catch (RPCException e) {
            e.printStackTrace();
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