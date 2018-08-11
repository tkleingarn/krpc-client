package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.kleingarn.FuelUtils.dropEmptyTanks;
import static com.kleingarn.FuelUtils.getDecoupleableParts;
import static java.util.stream.Collectors.toList;

public class RunVTOL {

    final static Logger logger = LoggerFactory.getLogger(RunVTOL.class);

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Flight");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        // assume we are flying already
        SpaceCenter.Vessel vessel = spaceCenter.getActiveVessel();
        runVTOL(spaceCenter, vessel);
    }

    private static void runVTOL(SpaceCenter spaceCenter, SpaceCenter.Vessel vessel) {

        SpaceCenter.Control vesselControl = null;
        SpaceCenter.AutoPilot vesselAutoPilot = null;

        logger.info("Running VTOL flight algorithm");
        try {
            vesselControl = vessel.getControl();
            vesselAutoPilot = vessel.getAutoPilot();

            // if lights are on and engines are not vertical

            // if lights changed
            boolean lightStatus = vesselControl.getLights();

            while (true) {

                if (lightStatus != vesselControl.getLights()) {
                    logger.info("Light changed from {} to {}", lightStatus, vesselControl.getLights());
                    lightStatus = vesselControl.getLights();

                    List<SpaceCenter.Part> partsWithDockingPorts = DockingUtils.getPartsWithDockingPorts(vessel);
                    DockingUtils.undockDockedPorts(vessel, partsWithDockingPorts);
                    Squadron vtolShipAndEngines = Squadron.buildSquadron("vtol docker 03", "vtol docker 03", spaceCenter);
                        // [main] INFO com.kleingarn.Squadron - Vessel found: vtol docker 03
                        // [main] INFO com.kleingarn.Squadron - Vessel found: vtol docker 03 Probe
                        // [main] INFO com.kleingarn.Squadron - Vessel found: vtol docker 03 Probe

                    if (lightStatus) {
                        changeEngineOrientation(vtolShipAndEngines, "probeCoreOcto2", 1.0F);
                        changeEngineOrientation(vtolShipAndEngines, "probeCoreOcto2", 1.0F);
                        changeEngineOrientation(vtolShipAndEngines, "probeCoreOcto2", 1.0F);
                    } else {
                        changeEngineOrientation(vtolShipAndEngines, "probeCoreOcto2", -1.0F);
                        changeEngineOrientation(vtolShipAndEngines, "probeCoreOcto2", -1.0F);
                        changeEngineOrientation(vtolShipAndEngines, "probeCoreOcto2", -1.0F);
                    }
                }
                sleep(5000);
            }
        } catch (RPCException e) {
            e.printStackTrace();
        }
    }

    private static void changeEngineOrientation(Squadron squadron,
                                                String controlFromPart,
                                                float pitch) {
        squadron.getSquadronVessels().
                parallelStream().
                filter(v -> !v.equals(squadron.getSquadLeader())).
                forEach
                    (v -> {
                        try {
                            logger.info("Setting {} SAS and SASMode", v.getName());
                            DockingUtils.setControlFromProbe(v, controlFromPart);
                            v.getControl().setSAS(true);
                            // v.getControl().setSASMode(orientation);
                            v.getControl().setPitch(pitch);
                        } catch (RPCException e) {
                            e.printStackTrace();
                        }
                    });
    }

    private static void sleep (int sleepTimeInmillis) {
        try {
            Thread.sleep(sleepTimeInmillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}