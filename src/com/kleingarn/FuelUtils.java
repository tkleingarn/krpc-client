package com.kleingarn;

import krpc.client.RPCException;
import krpc.client.services.SpaceCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class FuelUtils {

    final static Logger logger = LoggerFactory.getLogger(FuelUtils.class);

    public static void dropEmptyTanks(SpaceCenter.Vessel vessel){

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
        } catch (RPCException e) {
            e.printStackTrace();
        }
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
