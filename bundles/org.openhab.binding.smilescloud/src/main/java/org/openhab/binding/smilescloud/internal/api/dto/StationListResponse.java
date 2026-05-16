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

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Response from {@code /pvmc/api/0/station/select_by_page_c}.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class StationListResponse extends ApiResponse {

    public @Nullable StationListData data;

    @NonNullByDefault
    public static class StationListData {
        public @Nullable List<StationEntry> list;
        public @Nullable Integer total;
    }

    @NonNullByDefault
    public static class StationEntry {
        /** Station ID — Home profile uses "sid", Web/Installer uses "id". */
        public @Nullable Integer sid;
        public @Nullable Integer id;
        public @Nullable String name;

        public int getStationId() {
            Integer stationId = sid != null ? sid : id;
            return stationId != null ? stationId : 0;
        }

        public String getStationName() {
            String n = name;
            return n != null ? n : "Station " + getStationId();
        }
    }
}
