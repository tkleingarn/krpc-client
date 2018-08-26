package com.kleingarn;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.services.KRPC;
import krpc.client.services.SpaceCenter;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.kleingarn.FuelUtils.dropEmptyTanks;
import static com.kleingarn.FuelUtils.getDecoupleableParts;
import static java.util.stream.Collectors.toList;

public class RunVTOL {

    final static Logger logger = LoggerFactory.getLogger(RunVTOL.class);
    final static String craftName = "vtol ssto claw 02";

    public static void main(String[] args) throws IOException, RPCException {
        // init
        Connection connection = Connection.newInstance("Flight");
        KRPC krpc = KRPC.newInstance(connection);
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        logger.info("Connected to kRPC version {}", krpc.getStatus().getVersion());

        // assume we are flying already
        SpaceCenter.Vessel vessel = spaceCenter.getActiveVessel();

        logger.info("PARTS WITH CLAWS");
        DockingUtils.getPartsWithClaws(vessel);

        runVTOL(spaceCenter, vessel);
    }

    private static void runVTOL(SpaceCenter spaceCenter, SpaceCenter.Vessel vessel) {
        SpaceCenter.Control vesselControl = null;
        SpaceCenter.AutoPilot vesselAutoPilot = null;
        logger.info("Running VTOL flight algorithm");
        try {
            vesselControl = vessel.getControl();
            boolean lightStatus = vesselControl.getLights();
            
            while (true) {

                if (lightStatus != vesselControl.getLights()) {
                    logger.info("Light changed from {} to {}", lightStatus, vesselControl.getLights());
                    lightStatus = vesselControl.getLights();

                    DockingUtils.releaseClaws(vesselControl, 1);
                    sleep(100);
                    List<SpaceCenter.Part> partsWithDockingPorts = DockingUtils.getPartsWithDockingPorts(vessel);
                    DockingUtils.undockDockedPorts(vessel, partsWithDockingPorts);
                    Squadron vtolShipAndEngines = Squadron.buildSquadron(craftName, craftName, spaceCenter);
                    // note: "vessel name" is leader, "vessel name Probe" is probe on engine
                    
                    if (lightStatus) {
                        changeEngineOrientation(vtolShipAndEngines, "probeCoreOcto2", 1.0F, SpaceCenter.SASMode.RADIAL);
                    } else {
                        changeEngineOrientation(vtolShipAndEngines, "probeCoreOcto2", -1.0F, SpaceCenter.SASMode.ANTI_RADIAL);
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
                                                float pitch,
                                                SpaceCenter.SASMode orientation) {
        squadron.getSquadronVessels().
                parallelStream().
                filter(v -> !v.equals(squadron.getSquadLeader())).
                forEach
                    (v -> {
                        boolean docked = false;
                        try {
                            logger.info("Setting {} SAS and SASMode", v.getName());
                            DockingUtils.setControlFromProbe(v, controlFromPart);
                            // float reducer = pitch / 10;
                            float throttleIncrement = 0.005F;
                            int attempts = 0;
                            while(!docked) {
                                logger.info("Docked is: {}", docked);
                                v.getControl().setSAS(true);
                                v.getControl().setPitch(pitch);
                                // v.getControl().setSASMode(orientation); // too forceful
                                // v.getControl().setSASMode(SpaceCenter.SASMode.STABILITY_ASSIST);

                                if (pitch > 0) {
                                    if(throttleIncrement * attempts > 1.0F) {
                                        attempts = 0;
                                    } else {
                                        v.getControl().setThrottle(throttleIncrement * attempts++);
                                    }
                                } else {
                                    v.getControl().setSASMode(SpaceCenter.SASMode.STABILITY_ASSIST);
                                    if(throttleIncrement * attempts > 1.0F) {
                                        attempts = 0;
                                    } else {
                                        v.getControl().setThrottle(throttleIncrement * attempts++);
                                    }
                                }
                                docked = v.getParts().getDockingPorts().stream().anyMatch(
                                        dp -> {
                                            try {
                                                logger.info("Docking state of part {} is {}", dp, dp.getState());
                                                return dp.getState().equals(SpaceCenter.DockingPortState.DOCKED);
                                            } catch (RPCException e) {
                                                e.printStackTrace();
                                            }
                                            return false;
                                        }
                                );
                                sleep(500);
                            }
                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                            docked = true;
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