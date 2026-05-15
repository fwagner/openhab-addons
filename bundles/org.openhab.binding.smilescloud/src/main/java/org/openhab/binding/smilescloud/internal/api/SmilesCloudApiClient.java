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
package org.openhab.binding.smilescloud.internal.api;

import static org.openhab.binding.smilescloud.internal.SmilesCloudBindingConstants.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.smilescloud.internal.api.dto.PvIndicatorsData;
import org.openhab.binding.smilescloud.internal.api.dto.StationListResponse;
import org.openhab.binding.smilescloud.internal.api.dto.StationRealTimeData;
import org.openhab.binding.smilescloud.internal.exception.SmilesCloudApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * HTTP API client for the Hoymiles S-Miles Cloud.
 * All data API calls go through the bridge's auth service for token management.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class SmilesCloudApiClient {

    private final Logger logger = LoggerFactory.getLogger(SmilesCloudApiClient.class);
    private final HttpClient httpClient;
    private final SmilesCloudAuthService authService;
    private final Gson gson = new Gson();
    private String baseUrl;

    public SmilesCloudApiClient(HttpClient httpClient, SmilesCloudAuthService authService, String baseUrl) {
        this.httpClient = httpClient;
        this.authService = authService;
        this.baseUrl = baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    private String getDataHost() {
        String profile = authService.getProfile();
        String regionHost = authService.getRegionHost();
        String host = "home".equals(profile) ? DEFAULT_BASE_URL : (regionHost != null ? regionHost : baseUrl);
        logger.debug("Data host: {} (profile={})", host, profile);
        return host;
    }

    /**
     * Returns all stations as a map of stationId → stationName.
     */
    public Map<String, String> getStationList() throws SmilesCloudApiException {
        Map<String, String> stations = new LinkedHashMap<>();
        int pageNum = 1;
        int pageSize = 100;
        Integer total = null;

        while (true) {
            JsonObject body = new JsonObject();
            body.addProperty("page", pageNum);
            body.addProperty("page_size", pageSize);

            String response = authenticatedPost(getDataHost() + API_STATION_LIST_PATH, body);
            StationListResponse result = gson.fromJson(response, StationListResponse.class);
            if (result == null || !result.isSuccess() || result.data == null) {
                throw new SmilesCloudApiException(
                        "Failed to get stations: " + (result != null ? result.message : "null"));
            }

            StationListResponse.StationListData data = result.data;
            if (data == null) {
                break;
            }
            List<StationListResponse.StationEntry> list = data.list;
            if (total == null) {
                total = data.total;
            }

            if (list == null || list.isEmpty()) {
                break;
            }

            for (StationListResponse.StationEntry entry : list) {
                int id = entry.getStationId();
                if (id > 0) {
                    stations.put(String.valueOf(id), entry.getStationName());
                }
            }

            if (total != null && stations.size() >= total) {
                break;
            }
            if (list.size() < pageSize) {
                break;
            }
            pageNum++;
        }
        return stations;
    }

    /**
     * Fetches real-time data for a station.
     */
    public @Nullable StationRealTimeData getStationRealtime(String stationId) throws SmilesCloudApiException {
        JsonObject body = new JsonObject();
        body.addProperty("sid", Integer.parseInt(stationId));

        String response = authenticatedPost(getDataHost() + API_REALTIME_DATA_PATH, body);
        JsonObject parsed = JsonParser.parseString(response).getAsJsonObject();
        if (!"0".equals(getJsonString(parsed, "status"))) {
            throw new SmilesCloudApiException("Failed to get realtime data: " + getJsonString(parsed, "message"));
        }
        JsonObject dataObj = parsed.has("data") ? parsed.getAsJsonObject("data") : null;
        if (dataObj == null) {
            return null;
        }
        return gson.fromJson(dataObj, StationRealTimeData.class);
    }

    /**
     * Fetches PV indicator data for a station.
     */
    public @Nullable PvIndicatorsData getPvIndicators(String stationId) throws SmilesCloudApiException {
        JsonObject body = new JsonObject();
        body.addProperty("sid", Integer.parseInt(stationId));
        body.addProperty("type", 4);

        String response = authenticatedPost(getDataHost() + API_PV_INDICATORS_PATH, body);
        JsonObject parsed = JsonParser.parseString(response).getAsJsonObject();
        if (!"0".equals(getJsonString(parsed, "status"))) {
            throw new SmilesCloudApiException("Failed to get PV indicators: " + getJsonString(parsed, "message"));
        }
        JsonObject dataObj = parsed.has("data") ? parsed.getAsJsonObject("data") : null;
        if (dataObj == null) {
            return null;
        }
        return gson.fromJson(dataObj, PvIndicatorsData.class);
    }

    /**
     * Sets the power limit for a station.
     */
    public void setPowerLimit(String stationId, int powerLimit) throws SmilesCloudApiException {
        int clamped = Math.max(POWER_LIMIT_MIN, Math.min(POWER_LIMIT_MAX, powerLimit));

        JsonObject dataInner = new JsonObject();
        dataInner.addProperty("sid", Integer.parseInt(stationId));
        dataInner.addProperty("power_limit", clamped);
        dataInner.addProperty("enable", 1);

        JsonObject body = new JsonObject();
        body.addProperty("action", POWER_LIMIT_ACTION);
        body.add("data", dataInner);

        String response = authenticatedPost(getDataHost() + API_POWER_LIMIT_PATH, body);
        JsonObject parsed = JsonParser.parseString(response).getAsJsonObject();
        if (!"0".equals(getJsonString(parsed, "status"))) {
            throw new SmilesCloudApiException("Failed to set power limit: " + getJsonString(parsed, "message"));
        }
    }

    /**
     * Discovers PV channels from indicator data.
     */
    public static List<Integer> discoverPvChannels(@Nullable PvIndicatorsData indicators) {
        List<Integer> channels = new ArrayList<>();
        if (indicators == null || indicators.list == null) {
            return channels;
        }
        List<PvIndicatorsData.PvIndicatorEntry> entries = indicators.list;
        for (PvIndicatorsData.PvIndicatorEntry entry : entries) {
            String key = entry.key;
            if (key != null && key.endsWith("_pv_v")) {
                String prefix = key.substring(0, key.indexOf("_pv_v"));
                try {
                    int channel = Integer.parseInt(prefix);
                    if (!channels.contains(channel)) {
                        channels.add(channel);
                    }
                } catch (NumberFormatException e) {
                    // skip non-numeric prefixes
                }
            }
        }
        return channels;
    }

    /**
     * Gets a PV indicator value by key.
     */
    public static @Nullable String getPvIndicatorValue(@Nullable PvIndicatorsData indicators, String key) {
        if (indicators == null || indicators.list == null) {
            return null;
        }
        List<PvIndicatorsData.PvIndicatorEntry> entries = indicators.list;
        for (PvIndicatorsData.PvIndicatorEntry entry : entries) {
            if (key.equals(entry.key)) {
                return entry.val;
            }
        }
        return null;
    }

    // --- HTTP helper ---

    private String authenticatedPost(String url, JsonObject body) throws SmilesCloudApiException {
        String token = authService.getToken();
        if (token == null) {
            throw new SmilesCloudApiException("Not authenticated");
        }
        try {
            logger.debug("API POST {}", url);
            String response = postJson(url, body, token);
            logger.trace("API response: {}", response);
            return response;
        } catch (Exception e) {
            logger.debug("API POST {} failed: {}", url, e.getMessage());
            throw new SmilesCloudApiException("API request failed: " + e.getMessage(), e);
        }
    }

    private static final String APP_VERSION = "2.9.0";

    private String getUserAgent() {
        Integer dc = authService.getDataCenterMarker();
        return "sma/ad/" + APP_VERSION + "/159/" + (dc != null ? dc : 0);
    }

    private String postJson(String url, JsonObject body, String token)
            throws InterruptedException, TimeoutException, ExecutionException {
        ContentResponse response = httpClient.newRequest(url).method(HttpMethod.POST)
                .header("Content-Type", "application/json").header("Accept", "application/json").agent(getUserAgent())
                .header("App-Version", APP_VERSION).header("X-App-Version", APP_VERSION).header("Authorization", token)
                .timeout(HTTP_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .content(new StringContentProvider(gson.toJson(body))).send();
        return response.getContentAsString();
    }

    private static @Nullable String getJsonString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }
}
