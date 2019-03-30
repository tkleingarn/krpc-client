package com.kleingarn;

import krpc.client.RPCException;
import krpc.client.services.SpaceCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class FuelUtils {

    final static Logger logger = LoggerFactory.getLogger(FuelUtils.class);

    public static List<SpaceCenter.Part> getDecoupleableParts(SpaceCenter.Vessel vessel){

        try{
            List<SpaceCenter.Part> partsWithDecouplers = new ArrayList<>();
            for(SpaceCenter.Part part : vessel.getParts().getAll()) {
                if(part.getDecoupleStage() != -1) {
                    partsWithDecouplers.add(part);
                }
            }
            logger.info("Found {} parts with decouplers", partsWithDecouplers.size());
            return partsWithDecouplers;

        } catch (RPCException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static void activateNextStageIfFuelEmpty(SpaceCenter.Vessel vessel, List<SpaceCenter.Part> partsWithDecouplers) {

        logger.info("Checking if tanks are empty");
        try {
            if(areAnyTanksEmpty(vessel, partsWithDecouplers)) {
                try {
                    vessel.getControl().activateNextStage();
                } catch (RPCException e) {
                    e.printStackTrace();
                }
            }
        } catch (RPCException e) {
            e.printStackTrace();
        }
    }

    public static boolean areAnyTanksEmpty(SpaceCenter.Vessel vessel, List<SpaceCenter.Part> partsWithDecouplers) throws RPCException{
        for(SpaceCenter.Part part : partsWithDecouplers) {
            // only check the stage higher than the current stage)
            // This will save a few RPCs to the server for part.getResources().getAll()
            // currentStage - 1 = nextStage
            if(part.getDecoupleStage() >= vessel.getControl().getCurrentStage() - 1) {
                logger.info("[INFO] Checking if part {} is empty with decouple stage {} and vessel current stage {}",
                        part.getName(),
                        part.getDecoupleStage(),
                        vessel.getControl().getCurrentStage());
                List<SpaceCenter.Resource> resources = part.getResources().getAll();
                for (SpaceCenter.Resource resource : resources) {
                    if (resource.getAmount() == 0) {
                        logger.info("[INFO] Part {} is empty", part.getName());
                        return true;
                    }
                }
            }
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
