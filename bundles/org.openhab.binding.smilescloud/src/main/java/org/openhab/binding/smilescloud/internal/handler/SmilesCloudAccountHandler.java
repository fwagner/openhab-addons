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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SmilesCloudAccountHandler} is the bridge handler for a S-Miles Cloud account.
 * It manages authentication, token lifecycle, and polling for all child station things.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class SmilesCloudAccountHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(SmilesCloudAccountHandler.class);
    private final HttpClientFactory httpClientFactory;

    public SmilesCloudAccountHandler(Bridge bridge, HttpClientFactory httpClientFactory) {
        super(bridge);
        this.httpClientFactory = httpClientFactory;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        // TODO: validate config, authenticate, start polling
    }

    @Override
    public void dispose() {
        // TODO: cancel polling job, clean up resources
    }

    @Override
    public void handleRemoval() {
        // TODO: clear stored tokens from StorageService
        super.handleRemoval();
    }

    /**
     * Returns the discovered stations (id → name) for the discovery service.
     */
    public Map<String, String> getDiscoveredStations() {
        // TODO: return cached station map from last poll
        return Map.of();
    }
}
