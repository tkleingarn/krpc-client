package com.kleingarn;

import krpc.client.RPCException;
import krpc.client.services.SpaceCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FuelUtils {

    final static Logger logger = LoggerFactory.getLogger(FuelUtils.class);

    public static List<SpaceCenter.Part> getDecoupleableParts(SpaceCenter.Vessel vessel){

        try{
            List<SpaceCenter.Part> partsWithDecouplers = vessel.getParts().getAll();
            partsWithDecouplers.removeIf(part -> {
                try {
                    return part.getDecoupleStage() == -1;
                } catch (RPCException e) {
                    e.printStackTrace();
                }
                return false;
            });
            printParts(partsWithDecouplers);
            return partsWithDecouplers;
        } catch (RPCException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static void dropEmptyTanks(SpaceCenter.Vessel vessel, List<SpaceCenter.Part> partsWithDecouplers) {

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

    private static boolean areAnyTanksEmpty(SpaceCenter.Vessel vessel, List<SpaceCenter.Part> partsWithDecouplers) throws RPCException{
        for(SpaceCenter.Part part : partsWithDecouplers) {
            List<SpaceCenter.Resource> resources = part.getResources().getAll();
            for (SpaceCenter.Resource resource : resources) {
                if (resource.getAmount() == 0) {
                    return true;
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
