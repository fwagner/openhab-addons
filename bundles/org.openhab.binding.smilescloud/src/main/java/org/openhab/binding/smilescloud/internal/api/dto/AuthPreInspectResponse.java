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
 * Response from {@code /iam/pub/3/auth/pre-insp}.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class AuthPreInspectResponse extends ApiResponse {

    public @Nullable AuthPreInspectData data;

    @NonNullByDefault
    public static class AuthPreInspectData {
        /** Server-generated nonce. */
        public @Nullable String n;
        /** Argon2id salt as hex string. Null for unsalted accounts. */
        public @Nullable String a;
        /** Auth protocol version (3 = Argon2 era). */
        public @Nullable Integer v;
        /** Data center marker. */
        public @Nullable Integer dc;
        /** Account state flag (0 = normal, 1 = password expired, 2 = email-add required). */
        public @Nullable Integer f;
    }
}
