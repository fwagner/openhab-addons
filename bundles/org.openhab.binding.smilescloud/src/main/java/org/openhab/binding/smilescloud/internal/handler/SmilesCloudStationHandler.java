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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.smilescloud.internal.api.SmilesCloudApiClient;
import org.openhab.binding.smilescloud.internal.api.dto.PvIndicatorsData;
import org.openhab.binding.smilescloud.internal.api.dto.StationRealTimeData;
import org.openhab.binding.smilescloud.internal.api.dto.StationRealTimeData.RefluxStationData;
import org.openhab.binding.smilescloud.internal.config.SmilesCloudStationConfig;
import org.openhab.binding.smilescloud.internal.exception.SmilesCloudApiException;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SmilesCloudStationHandler} handles a single solar station.
 * It receives data from the bridge and updates channel states.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class SmilesCloudStationHandler extends BaseThingHandler {

    private static final DateTimeFormatter DATA_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern PV_VOLTAGE_CHANNEL_ID = Pattern.compile("^pv(\\d+)Voltage$");

    private final Logger logger = LoggerFactory.getLogger(SmilesCloudStationHandler.class);
    private final Set<Integer> createdPvChannels = new HashSet<>();

    public SmilesCloudStationHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            return;
        }

        if (CHANNEL_POWER_LIMIT.equals(channelUID.getId()) && command instanceof DecimalType decimalCommand) {
            handlePowerLimitCommand(decimalCommand.intValue());
        }
    }

    @Override
    public void initialize() {
        SmilesCloudStationConfig config = getConfigAs(SmilesCloudStationConfig.class);
        if (config.stationId.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Station ID must not be empty");
            return;
        }

        Bridge bridge = getBridge();
        if (bridge == null || bridge.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }

        syncCreatedPvChannelsFromThing();
        updateStatus(ThingStatus.UNKNOWN);
    }

    /**
     * Called by the bridge when real-time data is available.
     */
    public void updateData(StationRealTimeData data) {
        updatePowerChannel(CHANNEL_PV_POWER, data.realPower);
        updateEnergyChannel(CHANNEL_TODAY_ENERGY, data.todayEq);
        updateEnergyChannel(CHANNEL_MONTH_ENERGY, data.monthEq);
        updateEnergyChannel(CHANNEL_YEAR_ENERGY, data.yearEq);
        updateEnergyChannel(CHANNEL_TOTAL_ENERGY, data.totalEq);
        updateNumericChannel(CHANNEL_CO2_REDUCTION, data.co2EmissionReduction);
        updateTimestamp(data.dataTime);

        RefluxStationData reflux = data.refluxStationData;
        if (reflux != null) {
            updatePowerChannel(CHANNEL_GRID_POWER, reflux.gridPower);
            updatePowerChannel(CHANNEL_LOAD_POWER, reflux.loadPower);
            updatePowerChannel(CHANNEL_BATTERY_POWER, reflux.bmsPower);
            updatePercentChannel(CHANNEL_BATTERY_SOC, reflux.bmsSoc);
            updateEnergyChannel(CHANNEL_GRID_IMPORT_TODAY, reflux.meterBInEq);
            updateEnergyChannel(CHANNEL_GRID_EXPORT_TODAY, reflux.meterBOutEq);
            updateEnergyChannel(CHANNEL_BATTERY_CHARGE_TODAY, reflux.bmsInEq);
            updateEnergyChannel(CHANNEL_BATTERY_DISCHARGE_TODAY, reflux.bmsOutEq);
            updateEnergyChannel(CHANNEL_CONSUMPTION_TODAY, reflux.useEqTotal);
            updateEnergyChannel(CHANNEL_PV_TO_LOAD_TODAY, reflux.pvToLoadEq);

            StationRealTimeData.EnergyTotal mbIn = reflux.mbInEq;
            updateEnergyChannel(CHANNEL_GRID_IMPORT_TOTAL, mbIn != null ? mbIn.totalEq : null);
            StationRealTimeData.EnergyTotal mbOut = reflux.mbOutEq;
            updateEnergyChannel(CHANNEL_GRID_EXPORT_TOTAL, mbOut != null ? mbOut.totalEq : null);
        }

        updateStatus(ThingStatus.ONLINE);
    }

    /**
     * Called by the bridge when PV indicator data is available.
     */
    public void updatePvIndicators(PvIndicatorsData indicators) {
        List<Integer> channels = SmilesCloudApiClient.discoverPvChannels(indicators);

        if (!channels.isEmpty()) {
            ensureDynamicPvChannels(channels);
        }

        for (int ch : channels) {
            updateVoltageChannel("pv" + ch + "Voltage",
                    SmilesCloudApiClient.getPvIndicatorValue(indicators, ch + "_pv_v"));
            updateCurrentChannel("pv" + ch + "Current",
                    SmilesCloudApiClient.getPvIndicatorValue(indicators, ch + "_pv_i"));
            updatePowerChannel("pv" + ch + "Power", SmilesCloudApiClient.getPvIndicatorValue(indicators, ch + "_pv_p"));
        }

        updatePowerChannel(CHANNEL_PV_TOTAL_POWER, SmilesCloudApiClient.getPvIndicatorValue(indicators, "pv_p_total"));
    }

    /**
     * Called by the bridge when polling for this station fails.
     */
    public void setOffline(@Nullable String reason) {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                reason != null ? reason : "Communication error");
    }

    // --- Dynamic PV channels ---

    private void ensureDynamicPvChannels(List<Integer> pvChannelNumbers) {
        boolean changed = false;
        List<Channel> channels = new ArrayList<>(getThing().getChannels());

        for (int ch : pvChannelNumbers) {
            String voltageChannelId = "pv" + ch + "Voltage";
            if (createdPvChannels.contains(ch) || getThing().getChannel(voltageChannelId) != null) {
                createdPvChannels.add(ch);
                continue;
            }
            channels.add(buildDynamicChannel(voltageChannelId, "Number:ElectricPotential", "pvVoltage",
                    "PV" + ch + " Voltage"));
            channels.add(buildDynamicChannel("pv" + ch + "Current", "Number:ElectricCurrent", "pvCurrent",
                    "PV" + ch + " Current"));
            channels.add(
                    buildDynamicChannel("pv" + ch + "Power", "Number:Power", "pvStringPower", "PV" + ch + " Power"));
            createdPvChannels.add(ch);
            changed = true;
        }

        if (getThing().getChannel(CHANNEL_PV_TOTAL_POWER) == null) {
            channels.add(buildDynamicChannel(CHANNEL_PV_TOTAL_POWER, "Number:Power", "pvTotalPower", "PV Total Power"));
            changed = true;
        }

        if (changed) {
            updateThing(editThing().withChannels(channels).build());
        }
    }

    private void syncCreatedPvChannelsFromThing() {
        for (Channel channel : getThing().getChannels()) {
            Matcher matcher = PV_VOLTAGE_CHANNEL_ID.matcher(channel.getUID().getIdWithoutGroup());
            if (matcher.matches()) {
                createdPvChannels.add(Integer.parseInt(matcher.group(1)));
            }
        }
    }

    private Channel buildDynamicChannel(String channelId, String itemType, String channelTypeId, String label) {
        return ChannelBuilder.create(new ChannelUID(getThing().getUID(), channelId), itemType)
                .withType(new ChannelTypeUID(BINDING_ID, channelTypeId)).withLabel(label).build();
    }

    // --- Power limit command ---

    private void handlePowerLimitCommand(int value) {
        SmilesCloudStationConfig config = getConfigAs(SmilesCloudStationConfig.class);
        Bridge bridge = getBridge();
        if (bridge == null) {
            logger.warn("Cannot set power limit: no bridge");
            return;
        }
        if (bridge.getHandler() instanceof SmilesCloudAccountHandler accountHandler) {
            SmilesCloudApiClient api = accountHandler.getApiClient();
            if (api != null) {
                try {
                    api.setPowerLimit(config.stationId, value);
                    updateState(CHANNEL_POWER_LIMIT, new QuantityType<>(
                            Math.max(POWER_LIMIT_MIN, Math.min(POWER_LIMIT_MAX, value)), Units.PERCENT));
                } catch (SmilesCloudApiException e) {
                    logger.warn("Failed to set power limit: {}", e.getMessage());
                }
            }
        }
    }

    // --- Channel update helpers ---

    private void updatePowerChannel(String channelId, @Nullable String value) {
        updateState(channelId, parseQuantity(value, Units.WATT));
    }

    private void updateEnergyChannel(String channelId, @Nullable String value) {
        updateState(channelId, parseQuantity(value, Units.WATT_HOUR));
    }

    private void updateNumericChannel(String channelId, @Nullable String value) {
        if (value == null || value.isEmpty() || "-".equals(value)) {
            updateState(channelId, UnDefType.NULL);
            return;
        }
        try {
            updateState(channelId, new DecimalType(Double.parseDouble(value)));
        } catch (NumberFormatException e) {
            updateState(channelId, UnDefType.NULL);
        }
    }

    private void updatePercentChannel(String channelId, @Nullable String value) {
        updateState(channelId, parseQuantity(value, Units.PERCENT));
    }

    private void updateVoltageChannel(String channelId, @Nullable String value) {
        updateState(channelId, parseQuantity(value, Units.VOLT));
    }

    private void updateCurrentChannel(String channelId, @Nullable String value) {
        updateState(channelId, parseQuantity(value, Units.AMPERE));
    }

    private void updateTimestamp(@Nullable String dataTime) {
        if (dataTime == null || dataTime.isEmpty()) {
            updateState(CHANNEL_LAST_UPDATE, UnDefType.NULL);
            return;
        }
        try {
            LocalDateTime naive = LocalDateTime.parse(dataTime, DATA_TIME_FORMAT);
            ZonedDateTime zoned = naive.atZone(ZoneId.systemDefault());
            updateState(CHANNEL_LAST_UPDATE, new DateTimeType(zoned));
        } catch (Exception e) {
            logger.debug("Failed to parse data_time '{}': {}", dataTime, e.getMessage());
            updateState(CHANNEL_LAST_UPDATE, UnDefType.NULL);
        }
    }

    private static State parseQuantity(@Nullable String value, javax.measure.Unit<?> unit) {
        if (value == null || value.isEmpty() || "-".equals(value)) {
            return UnDefType.NULL;
        }
        try {
            double d = Double.parseDouble(value);
            return new QuantityType<>(d, unit);
        } catch (NumberFormatException e) {
            return UnDefType.NULL;
        }
    }
}
