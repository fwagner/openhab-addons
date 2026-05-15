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

/**
 * Generic API response envelope from the Hoymiles S-Miles Cloud.
 * All API calls return {@code {"status": "0", "message": "success", "data": {...}}}.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class ApiResponse {

    public @Nullable String status;
    public @Nullable String message;

    public boolean isSuccess() {
        return "0".equals(status) && "success".equals(message);
    }
}
