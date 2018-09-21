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

public class RunPartHighlighter {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    // structuralIBeam2
    // structuralIBeam3

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("i-Beam Machine Gun Fighter");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        SpaceCenter.Vessel vessel = spaceCenter.getActiveVessel();


        List<SpaceCenter.Decoupler> decouplers = vessel.getParts().getDecouplers();

        List<SpaceCenter.Part> iBeams = DockingUtils.getSpecificPartsOnVessel(vessel, "structuralIBeam2");

        final Triplet<Double, Double, Double> customHighlightColor = new Triplet<>(0.0,1.0,1.0);
//        for (SpaceCenter.Part iBeam : iBeams) {
//            iBeam.setHighlightColor(customHighlightColor);
//            iBeam.setHighlighted(true);
//        }

        for(SpaceCenter.Decoupler decoupler : decouplers) {
            List<SpaceCenter.Part> children = decoupler.getPart().getChildren();
            for (SpaceCenter.Part childPart : children) {
                childPart.setHighlightColor(customHighlightColor);
                childPart.setHighlighted(true);
            }
        }
        return;
    }

    private static void sleep (int sleepTimeInmillis) {
        try {
            Thread.sleep(sleepTimeInmillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}