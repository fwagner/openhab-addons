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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.openhab.binding.smilescloud.internal.SmilesCloudBindingConstants.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.smilescloud.internal.api.dto.PvIndicatorsData;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.test.java.JavaTest;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;

import com.google.gson.Gson;

/**
 * Tests for {@link SmilesCloudStationHandler}, especially dynamic PV channel creation.
 *
 * @author Florian Wagner - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@NonNullByDefault
class SmilesCloudStationHandlerTest extends JavaTest {

    private static final ThingUID BRIDGE_UID = new ThingUID(THING_TYPE_ACCOUNT, "testbridge");
    private static final ThingUID STATION_UID = new ThingUID(THING_TYPE_STATION, BRIDGE_UID, "station1");

    private final Gson gson = new Gson();

    @Mock
    private @NonNullByDefault({}) ThingHandlerCallback callback;

    @Test
    void updatePvIndicatorsCreatesChannelsOnFirstFetch() {
        Thing thing = stationThingBuilder().build();
        SmilesCloudStationHandler handler = createHandler(thing);

        handler.updatePvIndicators(samplePvIndicators());

        ArgumentCaptor<Thing> thingCaptor = ArgumentCaptor.forClass(Thing.class);
        verify(callback).thingUpdated(thingCaptor.capture());
        Set<String> channelIds = channelIds(thingCaptor.getValue());
        assertTrue(channelIds.contains("pv1Voltage"));
        assertTrue(channelIds.contains("pv1Current"));
        assertTrue(channelIds.contains("pv1Power"));
        assertTrue(channelIds.contains(CHANNEL_PV_TOTAL_POWER));
    }

    @Test
    void updatePvIndicatorsDoesNotDuplicateExistingChannels() {
        Thing thing = stationThingBuilder()
                .withChannel(pvChannel(1, "Voltage", "pvVoltage", "Number:ElectricPotential"))
                .withChannel(pvChannel(1, "Current", "pvCurrent", "Number:ElectricCurrent"))
                .withChannel(pvChannel(1, "Power", "pvStringPower", "Number:Power")).withChannel(totalPowerChannel())
                .build();
        SmilesCloudStationHandler handler = createHandler(thing);

        assertDoesNotThrow(() -> handler.updatePvIndicators(samplePvIndicators()));

        verify(callback, never()).thingUpdated(any());
    }

    @Test
    void updatePvIndicatorsAfterHandlerRestartDoesNotDuplicateChannels() {
        Thing thing = stationThingBuilder().build();

        SmilesCloudStationHandler firstHandler = createHandler(thing);
        firstHandler.updatePvIndicators(samplePvIndicators());

        ArgumentCaptor<Thing> thingCaptor = ArgumentCaptor.forClass(Thing.class);
        verify(callback).thingUpdated(thingCaptor.capture());
        Thing thingAfterFirstUpdate = thingCaptor.getValue();

        reset(callback);

        SmilesCloudStationHandler restartedHandler = createHandler(thingAfterFirstUpdate);
        restartedHandler.initialize();

        assertDoesNotThrow(() -> restartedHandler.updatePvIndicators(samplePvIndicators()));
        verify(callback, never()).thingUpdated(any());
    }

    private SmilesCloudStationHandler createHandler(Thing thing) {
        SmilesCloudStationHandler handler = new SmilesCloudStationHandler(thing);
        handler.setCallback(callback);
        doAnswer(invocation -> {
            handler.thingUpdated(invocation.getArgument(0));
            return null;
        }).when(callback).thingUpdated(any(Thing.class));
        return handler;
    }

    private ThingBuilder stationThingBuilder() {
        return ThingBuilder.create(THING_TYPE_STATION, STATION_UID)
                .withConfiguration(new Configuration(Map.of("stationId", "12345")));
    }

    private Channel pvChannel(int index, String suffix, String channelTypeId, String itemType) {
        String channelId = "pv" + index + suffix;
        return ChannelBuilder.create(new ChannelUID(STATION_UID, channelId), itemType)
                .withType(new ChannelTypeUID(BINDING_ID, channelTypeId)).withLabel(channelId).build();
    }

    private Channel totalPowerChannel() {
        return ChannelBuilder.create(new ChannelUID(STATION_UID, CHANNEL_PV_TOTAL_POWER), "Number:Power")
                .withType(new ChannelTypeUID(BINDING_ID, "pvTotalPower")).withLabel("PV Total Power").build();
    }

    private PvIndicatorsData samplePvIndicators() {
        String json = """
                {"list": [
                    {"key": "1_pv_v", "val": "32.5"},
                    {"key": "1_pv_i", "val": "8.2"},
                    {"key": "1_pv_p", "val": "266.5"},
                    {"key": "pv_p_total", "val": "528.0"}
                ]}""";
        PvIndicatorsData data = gson.fromJson(json, PvIndicatorsData.class);
        assertNotNull(data);
        return data;
    }

    private static Set<String> channelIds(Thing thing) {
        return thing.getChannels().stream().map(channel -> channel.getUID().getIdWithoutGroup())
                .collect(Collectors.toSet());
    }
}
