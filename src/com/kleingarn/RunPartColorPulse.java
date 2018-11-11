package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class RunPartColorPulse {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    // structuralIBeam2
    // structuralIBeam3

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Color pulse");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        SpaceCenter.Vessel vessel = spaceCenter.getActiveVessel();
        List<SpaceCenter.Part> parts = vessel.getParts().getAll();

        final Triplet<Double, Double, Double> customHighlightColor = new Triplet<>(0.0,1.0,1.0);
        final int highlightDelay = 250;

        while(true) {
            if(vessel.getControl().getActionGroup(5)) {
                for (SpaceCenter.Part part : parts) {
                    part.setHighlightColor(customHighlightColor);
                    part.setHighlighted(true);
                    sleep(highlightDelay);
                }
            } else {
                for (SpaceCenter.Part part : parts) {
                    part.setHighlighted(false);
                    sleep(highlightDelay);
                }
            }

//            SpaceCenter.Part root = vessel.getParts().getRoot();
//            Deque<Pair<SpaceCenter.Part, Integer>> stack = new ArrayDeque<Pair<SpaceCenter.Part, Integer>>();
//            stack.push(new Pair<SpaceCenter.Part, Integer>(root, 0));
//            while (stack.size() > 0) {
//                Pair<SpaceCenter.Part, Integer> item = stack.pop();
//                SpaceCenter.Part part = item.getValue0();
//                int depth = item.getValue1();
//                String prefix = "";
//                for (int i = 0; i < depth; i++) {
//                    prefix += " ";
//                    part.setHighlightColor(customHighlightColor);
//                    part.setHighlighted(true);
//                }
//                System.out.println(prefix + part.getTitle());
//                for (SpaceCenter.Part child : part.getChildren()) {
//                    stack.push(new Pair<SpaceCenter.Part, Integer>(child, depth + 1));
//                    part.setHighlighted(false);
//                }
//            }
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