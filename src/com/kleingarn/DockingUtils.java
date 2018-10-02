package com.kleingarn;

import krpc.client.RPCException;
import krpc.client.services.SpaceCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class DockingUtils {

    final static Logger logger = LoggerFactory.getLogger(DockingUtils.class);

    public static List<SpaceCenter.Part> getSpecificPartsOnVessel(SpaceCenter.Vessel vessel, String partName){
        try{
            List<SpaceCenter.Part> allParts = vessel.getParts().getAll();
            logger.info("Printing all parts on vessel, searching for {}", partName);
            printParts(allParts);
            allParts.removeIf(part -> {
                try {
                    return !part.getName().equals(partName);
                } catch (RPCException e) {
                    e.printStackTrace();
                }
                return false;
            });
            logger.info(partName + " count: " + allParts.size());
            printParts(allParts);
            return allParts;
        } catch (RPCException e) {
            e.printStackTrace();
        }
        return null;
    }

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

    public static List<SpaceCenter.Part> getPartsWithClaws(SpaceCenter.Vessel vessel){
        // [main] INFO com.kleingarn.DockingUtils - Part name: GrapplingDevice, Stage: -1
        try{
            List<SpaceCenter.Part> claws = vessel.getParts().getAll();
            claws.removeIf(part -> {
                try {
                    return !part.getName().equals("GrapplingDevice");
                } catch (RPCException e) {
                    e.printStackTrace();
                }
                return false;
            });
            printParts(claws);
            return claws;
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
                    try {
                        part.getDockingPort().undock();
                    } catch(UnsupportedOperationException e) {
                        logger.info("Trouble undocking part {}", part.getName());
                        e.printStackTrace();
                    }
                    logger.info("Undocked {}", part.getName());
                }
            } catch (RPCException e1) {
                e1.printStackTrace();
            }
        }
    }

//    public static void releaseClaws(SpaceCenter.Vessel vessel, List<SpaceCenter.Part> claws) {
//        logger.info("Checking if parts are docked");
//        for(SpaceCenter.Part part : claws) {
//            try {
//                if (part.() != null) {
//                    logger.info("Releasing claw {} named {}", part, part.getName());
////                logger.info("{} docking port is {} and state is {}", part.getName(), part.getDockingPort(), part.getDockingPort().getState());
//                    logger.info("{} decoupler state is {}", part.getName(), part.getDecoupler());
//                    // part.getDecoupler().decouple();
//                    part.getLaunchClamp().release();
//                    logger.info("Released {}", part.getName());
//                } else {
//                    logger.info("No claws to release cap'n!");
//                }
//            } catch (RPCException e1) {
//                e1.printStackTrace();
//            }
//        }
//    }

    public static void releaseClaws(SpaceCenter.Control vesselControl, int clawActionGroup) {
        try {
            boolean attached = vesselControl.getActionGroup(clawActionGroup);
            logger.info("Claw action group state is {}", attached);
            if (!attached) {
                vesselControl.toggleActionGroup(clawActionGroup);
                vesselControl.setActionGroup(clawActionGroup, false);
            }
        } catch (RPCException e1) {
            e1.printStackTrace();
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

    public static void printParts(List<SpaceCenter.Part> partsList) {
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
