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

import java.util.Locale;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Redacts sensitive fields from JSON before trace logging.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public final class SmilesCloudLogSanitizer {

    private static final String REDACTED = "***";
    private static final Set<String> SENSITIVE_KEYS = Set.of("token", "password", "ch", "authorization", "secret",
            "access_token", "refresh_token", "auth_token", "credential");

    private SmilesCloudLogSanitizer() {
    }

    /**
     * Returns a copy of the JSON string with known sensitive property values redacted.
     * Non-JSON input is replaced with a safe placeholder.
     */
    public static String sanitizeJsonForLog(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            redact(element);
            return element.toString();
        } catch (Exception e) {
            return "<non-json response>";
        }
    }

    private static void redact(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : object.keySet()) {
                JsonElement value = object.get(key);
                if (isSensitiveKey(key)) {
                    object.addProperty(key, REDACTED);
                } else {
                    redact(value);
                }
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                redact(item);
            }
        }
    }

    private static boolean isSensitiveKey(String key) {
        return SENSITIVE_KEYS.contains(key.toLowerCase(Locale.ROOT));
    }
}
