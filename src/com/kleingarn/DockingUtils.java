package com.kleingarn;

import krpc.client.RPCException;
import krpc.client.services.SpaceCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class DockingUtils {

    final static Logger logger = LoggerFactory.getLogger(DockingUtils.class);

    public static List<SpaceCenter.Part> getPartsWithDockingPorts(SpaceCenter.Vessel vessel){
        try{
            List<SpaceCenter.Part> dockingPorts = vessel.getParts().getAll();
            dockingPorts.removeIf(part -> {
                try {
                    return part.getDockingPort() == null;
                } catch (RPCException e) {
                    e.printStackTrace();
                }
                return false;
            });
            printParts(dockingPorts);
            return dockingPorts;
        } catch (RPCException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void undockDockedPorts(SpaceCenter.Vessel vessel, List<SpaceCenter.Part> partsWithDockingPorts) {
        logger.info("Checking if parts are docked");
        for(SpaceCenter.Part part : partsWithDockingPorts) {
            try {
                if (part.getDockingPort().getState().equals(SpaceCenter.DockingPortState.DOCKED)) {
                    part.getDockingPort().undock();
                    logger.info("Undocked {}", part.getName());
                }
            } catch (RPCException e1) {
                e1.printStackTrace();
            }
        }
    }

    public static boolean setControlFromProbe(SpaceCenter.Vessel vessel, String probePartName) {

        //[main] INFO com.kleingarn.DockingUtils - Part name: probeCoreOcto2, Stage: -1

        try {
            List<SpaceCenter.Part> allParts = vessel.getParts().getAll();
            printParts(allParts);

            for (SpaceCenter.Part p : allParts) {
                if (p.getName().equals(probePartName)) {
                    vessel.getParts().setControlling(p);
                    logger.info("Controlling {} from part {}", vessel, p);
                }
            }
        } catch (RPCException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void printParts(List<SpaceCenter.Part> partsList) {
        partsList.stream().forEach(
            p -> {
                try {
                logger.info("Part name: {}, Stage: {}", p.getName(), p.getDecoupleStage());
                } catch (RPCException e) {
                    e.printStackTrace();
                }
            });
    }
}
