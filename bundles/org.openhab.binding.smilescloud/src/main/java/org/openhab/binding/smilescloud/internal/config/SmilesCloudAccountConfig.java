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
package org.openhab.binding.smilescloud.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.smilescloud.internal.SmilesCloudBindingConstants;

/**
 * Configuration for the S-Miles Cloud account bridge.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class SmilesCloudAccountConfig {

    public String username = "";
    public String password = "";
    public int pollingInterval = SmilesCloudBindingConstants.DEFAULT_POLLING_INTERVAL_SECONDS;
    public String baseUrl = SmilesCloudBindingConstants.DEFAULT_BASE_URL;

    @Override
    public String toString() {
        return "SmilesCloudAccountConfig[username=" + username + ", password="
                + (password.isEmpty() ? "<empty>" : "***") + ", pollingInterval=" + pollingInterval + ", baseUrl="
                + baseUrl + "]";
    }
}
