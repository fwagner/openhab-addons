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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;

/**
 * Authentication service for the Hoymiles S-Miles Cloud.
 * Handles the full auth flow: region discovery, pre-inspect, Argon2id/unsalted v3 login,
 * profile probe, and token lifecycle.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class SmilesCloudAuthService {

    private final HttpClient httpClient;

    public SmilesCloudAuthService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    // TODO: discoverRegion, preInspect, loginV3, probeProfile, ensureToken
}
