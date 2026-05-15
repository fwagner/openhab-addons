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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Real-time station data from {@code count_station_real_data_c}.
 * All numeric values are strings in the API response.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class StationRealTimeData {

    @SerializedName("real_power")
    public @Nullable String realPower;

    @SerializedName("today_eq")
    public @Nullable String todayEq;

    @SerializedName("month_eq")
    public @Nullable String monthEq;

    @SerializedName("year_eq")
    public @Nullable String yearEq;

    @SerializedName("total_eq")
    public @Nullable String totalEq;

    @SerializedName("co2_emission_reduction")
    public @Nullable String co2EmissionReduction;

    @SerializedName("data_time")
    public @Nullable String dataTime;

    @SerializedName("reflux_station_data")
    public @Nullable RefluxStationData refluxStationData;

    @NonNullByDefault
    public static class RefluxStationData {
        @SerializedName("grid_power")
        public @Nullable String gridPower;

        @SerializedName("load_power")
        public @Nullable String loadPower;

        @SerializedName("bms_power")
        public @Nullable String bmsPower;

        @SerializedName("bms_soc")
        public @Nullable String bmsSoc;

        @SerializedName("meter_b_in_eq")
        public @Nullable String meterBInEq;

        @SerializedName("meter_b_out_eq")
        public @Nullable String meterBOutEq;

        @SerializedName("bms_in_eq")
        public @Nullable String bmsInEq;

        @SerializedName("bms_out_eq")
        public @Nullable String bmsOutEq;

        @SerializedName("use_eq_total")
        public @Nullable String useEqTotal;

        @SerializedName("pv_to_load_eq")
        public @Nullable String pvToLoadEq;

        @SerializedName("mb_in_eq")
        public @Nullable EnergyTotal mbInEq;

        @SerializedName("mb_out_eq")
        public @Nullable EnergyTotal mbOutEq;
    }

    @NonNullByDefault
    public static class EnergyTotal {
        @SerializedName("total_eq")
        public @Nullable String totalEq;
    }
}
