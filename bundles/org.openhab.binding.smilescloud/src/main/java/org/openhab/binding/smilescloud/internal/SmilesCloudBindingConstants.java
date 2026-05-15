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
package org.openhab.binding.smilescloud.internal;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link SmilesCloudBindingConstants} class defines common constants used across the binding.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class SmilesCloudBindingConstants {

    public static final String BINDING_ID = "smilescloud";

    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID THING_TYPE_STATION = new ThingTypeUID(BINDING_ID, "station");

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_ACCOUNT, THING_TYPE_STATION);

    // Station channels
    public static final String CHANNEL_PV_POWER = "pvPower";
    public static final String CHANNEL_TODAY_ENERGY = "todayEnergy";
    public static final String CHANNEL_MONTH_ENERGY = "monthEnergy";
    public static final String CHANNEL_YEAR_ENERGY = "yearEnergy";
    public static final String CHANNEL_TOTAL_ENERGY = "totalEnergy";
    public static final String CHANNEL_CO2_REDUCTION = "co2Reduction";
    public static final String CHANNEL_GRID_POWER = "gridPower";
    public static final String CHANNEL_LOAD_POWER = "loadPower";
    public static final String CHANNEL_BATTERY_POWER = "batteryPower";
    public static final String CHANNEL_BATTERY_SOC = "batterySoc";
    public static final String CHANNEL_GRID_IMPORT_TODAY = "gridImportToday";
    public static final String CHANNEL_GRID_EXPORT_TODAY = "gridExportToday";
    public static final String CHANNEL_BATTERY_CHARGE_TODAY = "batteryChargeToday";
    public static final String CHANNEL_BATTERY_DISCHARGE_TODAY = "batteryDischargeToday";
    public static final String CHANNEL_CONSUMPTION_TODAY = "consumptionToday";
    public static final String CHANNEL_PV_TO_LOAD_TODAY = "pvToLoadToday";
    public static final String CHANNEL_GRID_IMPORT_TOTAL = "gridImportTotal";
    public static final String CHANNEL_GRID_EXPORT_TOTAL = "gridExportTotal";
    public static final String CHANNEL_LAST_UPDATE = "lastUpdate";
    public static final String CHANNEL_POWER_LIMIT = "powerLimit";
    public static final String CHANNEL_PV_TOTAL_POWER = "pvTotalPower";

    // API defaults
    public static final String DEFAULT_BASE_URL = "https://neapi.hoymiles.com";
    public static final int DEFAULT_POLLING_INTERVAL_SECONDS = 120;
    public static final int MIN_POLLING_INTERVAL_SECONDS = 60;
    public static final int TOKEN_MAX_AGE_SECONDS = 3600;
    public static final int HTTP_REQUEST_TIMEOUT_SECONDS = 30;

    // API paths
    public static final String API_REGION_PATH = "/iam/pub/0/c/region_c";
    public static final String API_PRE_INSPECT_PATH = "/iam/pub/3/auth/pre-insp";
    public static final String API_LOGIN_V3_PATH = "/iam/pub/3/auth/login";
    public static final String API_PROFILE_PROBE_PATH = "/pvm/api/0/station/select_by_page";
    public static final String API_STATION_LIST_PATH = "/pvmc/api/0/station/select_by_page_c";
    public static final String API_STATION_DETAIL_PATH = "/pvmc/api/0/station/find_c";
    public static final String API_REALTIME_DATA_PATH = "/pvmc/api/0/station_data/count_station_real_data_c";
    public static final String API_PV_INDICATORS_PATH = "/pvm-data/api/0/indicators/data/select_real_indicators_data";
    public static final String API_DEVICE_TREE_PATH = "/pvmc/api/0/station/select_device_c";
    public static final String API_POWER_LIMIT_PATH = "/pvm-ctl/api/0/dev/command/put";

    // Power limit
    public static final int POWER_LIMIT_MIN = 5;
    public static final int POWER_LIMIT_MAX = 100;
    public static final int POWER_LIMIT_ACTION = 8;
}
