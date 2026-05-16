/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.smilescloud.internal.discovery;

import static org.openhab.binding.smilescloud.internal.SmilesCloudBindingConstants.*;

import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.smilescloud.internal.handler.SmilesCloudAccountHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovery service for S-Miles Cloud stations. Discovers stations
 * associated with the configured S-Miles Cloud account.
 *
 * @author Florian Wagner - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = SmilesCloudStationDiscoveryService.class)
@NonNullByDefault
public class SmilesCloudStationDiscoveryService
        extends AbstractThingHandlerDiscoveryService<SmilesCloudAccountHandler> {

    private static final int DISCOVERY_TIMEOUT_SECONDS = 10;
    private final Logger logger = LoggerFactory.getLogger(SmilesCloudStationDiscoveryService.class);

    public SmilesCloudStationDiscoveryService() {
        super(SmilesCloudAccountHandler.class, Set.of(THING_TYPE_STATION), DISCOVERY_TIMEOUT_SECONDS, true);
    }

    @Override
    public void initialize() {
        thingHandler.setDiscoveryService(this);
        super.initialize();
    }

    @Override
    public void dispose() {
        super.dispose();
        thingHandler.setDiscoveryService(null);
    }

    @Override
    protected void startScan() {
        SmilesCloudAccountHandler bridge = getThingHandler();
        if (bridge == null) {
            return;
        }
        Map<String, String> stations = bridge.getDiscoveredStations();
        ThingUID bridgeUID = bridge.getThing().getUID();

        for (Map.Entry<String, String> entry : stations.entrySet()) {
            ThingUID thingUID = new ThingUID(THING_TYPE_STATION, bridgeUID, entry.getKey());
            thingDiscovered(DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID).withLabel(entry.getValue())
                    .withProperty("stationId", entry.getKey()).withRepresentationProperty("stationId").build());
        }
    }

    /**
     * Called by the bridge handler to publish auto-discovered stations to the inbox.
     */
    public void publishDiscoveryResult(DiscoveryResult result) {
        thingDiscovered(result);
    }
}
