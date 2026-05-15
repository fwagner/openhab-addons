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
package org.openhab.binding.smilescloud.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.smilescloud.internal.api.dto.StationRealTimeData;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SmilesCloudStationHandler} handles a single solar station from the S-Miles Cloud.
 * It receives data updates from the bridge and updates channel states accordingly.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class SmilesCloudStationHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(SmilesCloudStationHandler.class);

    public SmilesCloudStationHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO: handle powerLimit write commands, RefreshType
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        // TODO: validate config, register with bridge
    }

    @Override
    public void dispose() {
        // TODO: unregister from bridge
    }

    /**
     * Called by the bridge handler when new data is available for this station.
     */
    public void updateData(StationRealTimeData data) {
        // TODO: update all channel states from data
    }
}
