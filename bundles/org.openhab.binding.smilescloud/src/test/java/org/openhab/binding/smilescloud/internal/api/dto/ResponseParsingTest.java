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
package org.openhab.binding.smilescloud.internal.api.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.smilescloud.internal.api.SmilesCloudApiClient;

import com.google.gson.Gson;

/**
 * Tests for API response DTO parsing.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
class ResponseParsingTest {

    private final Gson gson = new Gson();

    @Test
    void testParseSuccessResponse() {
        String json = "{\"status\": \"0\", \"message\": \"success\"}";
        ApiResponse resp = gson.fromJson(json, ApiResponse.class);
        assertNotNull(resp);
        assertTrue(resp.isSuccess());
    }

    @Test
    void testParseErrorResponse() {
        String json = "{\"status\": \"1\", \"message\": \"invalid credentials\"}";
        ApiResponse resp = gson.fromJson(json, ApiResponse.class);
        assertNotNull(resp);
        assertFalse(resp.isSuccess());
        assertEquals("invalid credentials", resp.message);
    }

    @Test
    void testParsePreInspectWithSalt() {
        String json = """
                {"status": "0", "message": "success", "data": {
                    "n": "abc123", "a": "d5e3f019748d7a36d69840fdfd873d15", "v": 3, "dc": 0, "f": 0
                }}""";
        AuthPreInspectResponse resp = gson.fromJson(json, AuthPreInspectResponse.class);
        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        assertNotNull(resp.data);
        assertEquals("abc123", resp.data.n);
        assertEquals("d5e3f019748d7a36d69840fdfd873d15", resp.data.a);
        assertEquals(Integer.valueOf(3), resp.data.v);
        assertEquals(Integer.valueOf(0), resp.data.f);
    }

    @Test
    void testParsePreInspectWithoutSalt() {
        String json = """
                {"status": "0", "message": "success", "data": {
                    "n": "xyz789", "a": null, "v": 2
                }}""";
        AuthPreInspectResponse resp = gson.fromJson(json, AuthPreInspectResponse.class);
        assertNotNull(resp);
        assertNotNull(resp.data);
        assertEquals("xyz789", resp.data.n);
        assertNull(resp.data.a);
    }

    @Test
    void testParseLoginResponse() {
        String json = """
                {"status": "0", "message": "success", "data": {
                    "token": "eyJhbGciOiJIUzI1NiJ9.test.signature"
                }}""";
        AuthLoginResponse resp = gson.fromJson(json, AuthLoginResponse.class);
        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        assertNotNull(resp.data);
        assertEquals("eyJhbGciOiJIUzI1NiJ9.test.signature", resp.data.token);
    }

    @Test
    void testParseRegionResponse() {
        String json = """
                {"status": "0", "message": "success", "data": {
                    "login_url": "https://euapi.hoymiles.com", "dc": 1
                }}""";
        RegionResponse resp = gson.fromJson(json, RegionResponse.class);
        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        assertNotNull(resp.data);
        assertEquals("https://euapi.hoymiles.com", resp.data.loginUrl);
        assertEquals(Integer.valueOf(1), resp.data.dc);
    }

    @Test
    void testParseStationListWithSid() {
        String json = """
                {"status": "0", "message": "success", "data": {
                    "list": [{"sid": 12345, "name": "My Station"}], "total": 1
                }}""";
        StationListResponse resp = gson.fromJson(json, StationListResponse.class);
        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        assertNotNull(resp.data);
        assertNotNull(resp.data.list);
        assertEquals(1, resp.data.list.size());
        assertEquals(12345, resp.data.list.get(0).getStationId());
        assertEquals("My Station", resp.data.list.get(0).getStationName());
    }

    @Test
    void testParseStationListWithId() {
        String json = """
                {"status": "0", "message": "success", "data": {
                    "list": [{"id": 99, "name": "Web Station"}], "total": 1
                }}""";
        StationListResponse resp = gson.fromJson(json, StationListResponse.class);
        assertNotNull(resp);
        assertNotNull(resp.data);
        assertNotNull(resp.data.list);
        assertEquals(99, resp.data.list.get(0).getStationId());
    }

    @Test
    void testParseStationListNoName() {
        String json = """
                {"status": "0", "message": "success", "data": {
                    "list": [{"sid": 42}], "total": 1
                }}""";
        StationListResponse resp = gson.fromJson(json, StationListResponse.class);
        assertNotNull(resp);
        assertNotNull(resp.data);
        assertNotNull(resp.data.list);
        assertEquals("Station 42", resp.data.list.get(0).getStationName());
    }

    @Test
    void testParseRealtimeData() {
        String json = """
                {
                    "real_power": "1234.5", "today_eq": "5678", "month_eq": "123456",
                    "year_eq": "1234567", "total_eq": "12345678",
                    "co2_emission_reduction": "9876", "data_time": "2026-05-15 14:30:00",
                    "reflux_station_data": {
                        "grid_power": "100.5", "load_power": "800",
                        "bms_power": "200", "bms_soc": "75",
                        "meter_b_in_eq": "1000", "meter_b_out_eq": "2000",
                        "bms_in_eq": "500", "bms_out_eq": "300",
                        "use_eq_total": "4500", "pv_to_load_eq": "3000",
                        "mb_in_eq": {"total_eq": "50000"},
                        "mb_out_eq": {"total_eq": "80000"}
                    }
                }""";
        StationRealTimeData data = gson.fromJson(json, StationRealTimeData.class);
        assertNotNull(data);
        assertEquals("1234.5", data.realPower);
        assertEquals("5678", data.todayEq);
        assertEquals("2026-05-15 14:30:00", data.dataTime);
        assertNotNull(data.refluxStationData);
        assertEquals("100.5", data.refluxStationData.gridPower);
        assertEquals("75", data.refluxStationData.bmsSoc);
        assertNotNull(data.refluxStationData.mbInEq);
        assertEquals("50000", data.refluxStationData.mbInEq.totalEq);
    }

    @Test
    void testParseRealtimeDataNullReflux() {
        String json = """
                {"real_power": "500", "today_eq": "1000", "data_time": "2026-05-15 12:00:00"}""";
        StationRealTimeData data = gson.fromJson(json, StationRealTimeData.class);
        assertNotNull(data);
        assertEquals("500", data.realPower);
        assertNull(data.refluxStationData);
    }

    @Test
    void testParseRealtimeDataNullFields() {
        String json = """
                {"real_power": null, "today_eq": "", "month_eq": "-"}""";
        StationRealTimeData data = gson.fromJson(json, StationRealTimeData.class);
        assertNotNull(data);
        assertNull(data.realPower);
        assertEquals("", data.todayEq);
        assertEquals("-", data.monthEq);
    }

    @Test
    void testParsePvIndicators() {
        String json = """
                {"list": [
                    {"key": "1_pv_v", "val": "32.5"},
                    {"key": "1_pv_i", "val": "8.2"},
                    {"key": "1_pv_p", "val": "266.5"},
                    {"key": "2_pv_v", "val": "33.1"},
                    {"key": "2_pv_i", "val": "7.9"},
                    {"key": "2_pv_p", "val": "261.5"},
                    {"key": "pv_p_total", "val": "528.0"}
                ]}""";
        PvIndicatorsData data = gson.fromJson(json, PvIndicatorsData.class);
        assertNotNull(data);
        assertNotNull(data.list);
        assertEquals(7, data.list.size());

        List<Integer> channels = SmilesCloudApiClient.discoverPvChannels(data);
        assertEquals(List.of(1, 2), channels);

        assertEquals("32.5", SmilesCloudApiClient.getPvIndicatorValue(data, "1_pv_v"));
        assertEquals("528.0", SmilesCloudApiClient.getPvIndicatorValue(data, "pv_p_total"));
        assertNull(SmilesCloudApiClient.getPvIndicatorValue(data, "3_pv_v"));
    }

    @Test
    void testParsePvIndicatorsEmpty() {
        String json = """
                {"list": []}""";
        PvIndicatorsData data = gson.fromJson(json, PvIndicatorsData.class);
        assertNotNull(data);
        List<Integer> channels = SmilesCloudApiClient.discoverPvChannels(data);
        assertTrue(channels.isEmpty());
    }

    @Test
    void testDiscoverPvChannelsNull() {
        List<Integer> channels = SmilesCloudApiClient.discoverPvChannels(null);
        assertTrue(channels.isEmpty());
    }

    @Test
    void testGetPvIndicatorValueNull() {
        assertNull(SmilesCloudApiClient.getPvIndicatorValue(null, "1_pv_v"));
    }
}
