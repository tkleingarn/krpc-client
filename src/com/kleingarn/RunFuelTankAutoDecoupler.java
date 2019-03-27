package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static com.kleingarn.FuelUtils.activateNextStageIfFuelEmpty;
import static com.kleingarn.FuelUtils.getDecoupleableParts;

public class RunFuelTankAutoDecoupler {

    final static Logger logger = LoggerFactory.getLogger(Squadron.class);

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Drop tanks automatically");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        while (true) {
            SpaceCenter.Vessel activeVessel = spaceCenter.getActiveVessel();
            List<SpaceCenter.Part> partsWithDecouplers = getDecoupleableParts(activeVessel);
            activateNextStageIfFuelEmpty(activeVessel, partsWithDecouplers);
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