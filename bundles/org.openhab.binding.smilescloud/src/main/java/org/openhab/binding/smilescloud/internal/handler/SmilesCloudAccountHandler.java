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

import static org.openhab.binding.smilescloud.internal.SmilesCloudBindingConstants.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.smilescloud.internal.api.SmilesCloudApiClient;
import org.openhab.binding.smilescloud.internal.api.SmilesCloudAuthService;
import org.openhab.binding.smilescloud.internal.api.dto.PvIndicatorsData;
import org.openhab.binding.smilescloud.internal.api.dto.StationRealTimeData;
import org.openhab.binding.smilescloud.internal.config.SmilesCloudAccountConfig;
import org.openhab.binding.smilescloud.internal.discovery.SmilesCloudStationDiscoveryService;
import org.openhab.binding.smilescloud.internal.exception.SmilesCloudApiException;
import org.openhab.binding.smilescloud.internal.exception.SmilesCloudAuthenticationException;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.storage.Storage;
import org.openhab.core.storage.StorageService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SmilesCloudAccountHandler} is the bridge handler for a S-Miles Cloud account.
 * It manages authentication, token lifecycle, polling, and pushes data to child station handlers.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class SmilesCloudAccountHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(SmilesCloudAccountHandler.class);
    private final HttpClientFactory httpClientFactory;
    private final StorageService storageService;

    private @Nullable SmilesCloudAuthService authService;
    private @Nullable SmilesCloudApiClient apiClient;
    private @Nullable ScheduledFuture<?> pollingJob;
    private @Nullable Storage<String> storage;

    private final Map<String, String> discoveredStations = new ConcurrentHashMap<>();

    public SmilesCloudAccountHandler(Bridge bridge, HttpClientFactory httpClientFactory,
            StorageService storageService) {
        super(bridge);
        this.httpClientFactory = httpClientFactory;
        this.storageService = storageService;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(SmilesCloudStationDiscoveryService.class);
    }

    @Override
    public void initialize() {
        SmilesCloudAccountConfig config = getConfigAs(SmilesCloudAccountConfig.class);

        if (config.username.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Username must not be empty");
            return;
        }
        if (config.password.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Password must not be empty");
            return;
        }
        if (!config.baseUrl.startsWith("https://")) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Base URL must use HTTPS for security");
            return;
        }
        if (config.pollingInterval < MIN_POLLING_INTERVAL_SECONDS) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Polling interval must be at least " + MIN_POLLING_INTERVAL_SECONDS + " seconds");
            return;
        }

        logger.debug("Initializing S-Miles Cloud account bridge for user {}", config.username);
        updateStatus(ThingStatus.UNKNOWN);

        var httpClient = httpClientFactory.getCommonHttpClient();
        SmilesCloudAuthService auth = new SmilesCloudAuthService(httpClient);
        SmilesCloudApiClient api = new SmilesCloudApiClient(httpClient, auth, config.baseUrl);
        this.authService = auth;
        this.apiClient = api;

        Storage<String> s = storageService.getStorage(thing.getUID().toString());
        this.storage = s;
        restoreAuthState(auth, s);

        logger.debug("Starting poll scheduler with interval {}s", config.pollingInterval);
        pollingJob = scheduler.scheduleWithFixedDelay(this::poll, 0, config.pollingInterval, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(true);
            pollingJob = null;
        }
        authService = null;
        apiClient = null;
    }

    @Override
    public void handleRemoval() {
        Storage<String> s = storage;
        if (s != null) {
            s.remove("authToken");
            s.remove("authMethod");
            s.remove("tokenExpiry");
            s.remove("profile");
            s.remove("regionHost");
            s.remove("dc");
        }
        super.handleRemoval();
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        super.handleConfigurationUpdate(configurationParameters);
        dispose();
        initialize();
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof SmilesCloudStationHandler stationHandler) {
            String stationId = (String) childThing.getConfiguration().get("stationId");
            if (stationId != null) {
                logger.debug("Station thing {} adopted, fetching data immediately", stationId);
                SmilesCloudApiClient api = apiClient;
                SmilesCloudAuthService auth = authService;
                SmilesCloudAccountConfig config = getConfigAs(SmilesCloudAccountConfig.class);
                if (api != null && auth != null) {
                    scheduler.execute(() -> pollStation(api, stationId, auth, config));
                }
            }
        }
    }

    public Map<String, String> getDiscoveredStations() {
        return Map.copyOf(discoveredStations);
    }

    public @Nullable SmilesCloudApiClient getApiClient() {
        return apiClient;
    }

    public @Nullable SmilesCloudAuthService getAuthService() {
        return authService;
    }

    // --- Polling ---

    private void poll() {
        logger.debug("Poll cycle starting");
        SmilesCloudAuthService auth = authService;
        SmilesCloudApiClient api = apiClient;
        if (auth == null || api == null) {
            logger.debug("Auth or API client not initialized, skipping poll");
            return;
        }

        SmilesCloudAccountConfig config = getConfigAs(SmilesCloudAccountConfig.class);

        try {
            logger.debug("Ensuring authentication for {}", config.username);
            auth.ensureAuthenticated(config.baseUrl, config.username, config.password);
            logger.debug("Authentication OK, token valid={}, profile={}", auth.isTokenValid(), auth.getProfile());
            persistAuthState(auth);
        } catch (SmilesCloudAuthenticationException e) {
            String msg = e.getMessage();
            logger.warn("Authentication failed: {}", msg, e);
            if (msg != null && (msg.contains("Password expired") || msg.contains("invalid credentials")
                    || msg.contains("check your account"))) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Authentication failed: " + msg);
            }
            return;
        }

        try {
            logger.debug("Fetching station list...");
            Map<String, String> stations = api.getStationList();
            logger.debug("Station list returned {} station(s): {}", stations.size(), stations);
            discoveredStations.clear();
            discoveredStations.putAll(stations);
            notifyDiscovery(stations);

            if (stations.isEmpty()) {
                updateStatus(ThingStatus.ONLINE);
                logger.info("No stations found for this account");
                return;
            }

            for (String stationId : stations.keySet()) {
                pollStation(api, stationId, auth, config);
            }
            updateStatus(ThingStatus.ONLINE);
        } catch (SmilesCloudApiException e) {
            logger.debug("API error during poll: {}", e.getMessage());
            updateStatus(ThingStatus.ONLINE);
        }
    }

    private void pollStation(SmilesCloudApiClient api, String stationId, SmilesCloudAuthService auth,
            SmilesCloudAccountConfig config) {
        try {
            StationRealTimeData realtime = api.getStationRealtime(stationId);
            PvIndicatorsData pvIndicators = api.getPvIndicators(stationId);

            for (Thing child : getThing().getThings()) {
                if (child.getHandler() instanceof SmilesCloudStationHandler stationHandler) {
                    String childStationId = (String) child.getConfiguration().get("stationId");
                    if (stationId.equals(childStationId)) {
                        try {
                            if (realtime != null) {
                                stationHandler.updateData(realtime);
                            }
                            if (pvIndicators != null) {
                                stationHandler.updatePvIndicators(pvIndicators);
                            }
                        } catch (RuntimeException e) {
                            logger.warn("Unexpected error updating station {}: {}", stationId, e.getMessage(), e);
                            stationHandler.setOffline(e.getMessage());
                        }
                    }
                }
            }
        } catch (SmilesCloudApiException e) {
            logger.debug("Failed to poll station {}: {}", stationId, e.getMessage());
            for (Thing child : getThing().getThings()) {
                if (child.getHandler() instanceof SmilesCloudStationHandler stationHandler) {
                    String childStationId = (String) child.getConfiguration().get("stationId");
                    if (stationId.equals(childStationId)) {
                        stationHandler.setOffline(e.getMessage());
                    }
                }
            }
        }
    }

    // --- Auto-discovery ---

    private @Nullable SmilesCloudStationDiscoveryService discoveryService;

    public void setDiscoveryService(@Nullable SmilesCloudStationDiscoveryService service) {
        this.discoveryService = service;
    }

    private void notifyDiscovery(Map<String, String> stations) {
        SmilesCloudStationDiscoveryService svc = discoveryService;
        if (svc == null) {
            logger.debug("Discovery service not registered, skipping auto-discovery");
            return;
        }
        ThingUID bridgeUID = getThing().getUID();
        for (Map.Entry<String, String> entry : stations.entrySet()) {
            String stationId = entry.getKey();
            boolean alreadyAdopted = getThing().getThings().stream()
                    .anyMatch(t -> stationId.equals(t.getConfiguration().get("stationId")));
            if (alreadyAdopted) {
                continue;
            }
            ThingUID thingUID = new ThingUID(THING_TYPE_STATION, bridgeUID, stationId);
            DiscoveryResult result = DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID)
                    .withLabel(entry.getValue()).withProperty("stationId", stationId)
                    .withRepresentationProperty("stationId").build();
            logger.debug("Auto-discovered station {} ({})", stationId, entry.getValue());
            svc.publishDiscoveryResult(result);
        }
    }

    // --- Auth State Persistence ---

    private void restoreAuthState(SmilesCloudAuthService auth, Storage<String> s) {
        String token = s.get("authToken");
        String method = s.get("authMethod");
        String expiry = s.get("tokenExpiry");
        String savedProfile = s.get("profile");
        String savedRegionHost = s.get("regionHost");
        String dcStr = s.get("dc");
        Integer dc = dcStr != null ? Integer.valueOf(dcStr) : null;
        auth.restoreState(token, method, expiry, savedProfile, savedRegionHost, dc);
    }

    private void persistAuthState(SmilesCloudAuthService auth) {
        Storage<String> s = storage;
        if (s == null) {
            return;
        }
        String token = auth.getToken();
        if (token != null) {
            s.put("authToken", token);
        }
        String profile = auth.getProfile();
        if (profile != null) {
            s.put("profile", profile);
        }
        String regionHost = auth.getRegionHost();
        if (regionHost != null) {
            s.put("regionHost", regionHost);
        }
        Integer dc = auth.getDataCenterMarker();
        if (dc != null) {
            s.put("dc", dc.toString());
        }
        s.put("tokenExpiry", auth.isTokenValid() ? java.time.Instant.now().plusSeconds(TOKEN_MAX_AGE_SECONDS).toString()
                : java.time.Instant.EPOCH.toString());
    }
}
