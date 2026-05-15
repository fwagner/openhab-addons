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
 * Tests for credential hash computations in {@link SmilesCloudAuthService}.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
class SmilesCloudAuthServiceTest {

    @Test
    void testArgon2idKnownVector() {
        String result = SmilesCloudAuthService.computeArgon2Challenge("testpassword123",
                "d5e3f019748d7a36d69840fdfd873d15");
        assertEquals("3c5d1ece590f242aa94b901f3940ebfd89b7bd0fdd21132a69e4321d6436a409", result);
    }

    @Test
    void testArgon2idEmptyPassword() {
        assertDoesNotThrow(() -> SmilesCloudAuthService.computeArgon2Challenge("", "abcdef0123456789abcdef0123456789"));
    }

    @Test
    void testArgon2idLongPassword() {
        String longPw = "a".repeat(1500);
        assertDoesNotThrow(
                () -> SmilesCloudAuthService.computeArgon2Challenge(longPw, "abcdef0123456789abcdef0123456789"));
    }

    @Test
    void testArgon2idOutputLength() {
        String result = SmilesCloudAuthService.computeArgon2Challenge("test", "abcdef0123456789abcdef0123456789");
        assertEquals(64, result.length(), "Argon2id hash should be 32 bytes = 64 hex chars");
    }

    @Test
    void testSha256V3Format() {
        String result = SmilesCloudAuthService.computeSha256V3Challenge("testpassword123");
        assertTrue(result.contains("."), "sha256_v3 should contain a dot separator");
        String[] parts = result.split("\\.", 2);
        assertEquals(32, parts[0].length(), "MD5 hex should be 32 chars");
        assertFalse(parts[1].isEmpty(), "Base64 SHA-256 should not be empty");
    }

    @Test
    void testSha256HexFormat() {
        String result = SmilesCloudAuthService.computeSha256HexChallenge("testpassword123");
        assertEquals(64, result.length(), "SHA-256 hex should be 64 chars");
        assertTrue(result.matches("[0-9a-f]+"), "SHA-256 hex should be lowercase hex");
    }

    @Test
    void testSha256V3Deterministic() {
        String a = SmilesCloudAuthService.computeSha256V3Challenge("hello");
        String b = SmilesCloudAuthService.computeSha256V3Challenge("hello");
        assertEquals(a, b);
    }

    @Test
    void testSha256HexDeterministic() {
        String a = SmilesCloudAuthService.computeSha256HexChallenge("hello");
        String b = SmilesCloudAuthService.computeSha256HexChallenge("hello");
        assertEquals(a, b);
    }
}
