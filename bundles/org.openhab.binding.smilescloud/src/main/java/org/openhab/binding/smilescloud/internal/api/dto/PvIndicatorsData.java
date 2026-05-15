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
 * PV indicators data from {@code select_real_indicators_data}.
 * Contains per-PV-string voltage, current, and power readings.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class PvIndicatorsData {

    public @Nullable List<PvIndicatorEntry> list;

    @NonNullByDefault
    public static class PvIndicatorEntry {
        public @Nullable String key;
        public @Nullable String val;
    }
}
