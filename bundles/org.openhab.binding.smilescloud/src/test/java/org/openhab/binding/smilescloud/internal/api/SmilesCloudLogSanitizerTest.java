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

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SmilesCloudLogSanitizer}.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
class SmilesCloudLogSanitizerTest {

    @Test
    void redactsTokenInNestedObject() {
        String json = """
                {"status":"0","data":{"token":"secret-token","profile":"home"}}""";
        String sanitized = SmilesCloudLogSanitizer.sanitizeJsonForLog(json);
        assertFalse(sanitized.contains("secret-token"));
        assertTrue(sanitized.contains("\"token\":\"***\""));
        assertTrue(sanitized.contains("\"profile\":\"home\""));
    }

    @Test
    void redactsCredentialChallenge() {
        String json = """
                {"u":"user@example.com","ch":"long-credential-hash","n":"nonce123"}""";
        String sanitized = SmilesCloudLogSanitizer.sanitizeJsonForLog(json);
        assertFalse(sanitized.contains("long-credential-hash"));
        assertTrue(sanitized.contains("\"ch\":\"***\""));
        assertTrue(sanitized.contains("\"n\":\"nonce123\""));
    }

    @Test
    void nonJsonReturnsPlaceholder() {
        assertEquals("<non-json response>", SmilesCloudLogSanitizer.sanitizeJsonForLog("not json"));
    }
}
