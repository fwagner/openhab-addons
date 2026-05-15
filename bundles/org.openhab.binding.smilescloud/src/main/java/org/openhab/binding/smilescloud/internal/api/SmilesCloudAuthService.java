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

import static org.openhab.binding.smilescloud.internal.SmilesCloudBindingConstants.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.smilescloud.internal.api.dto.AuthPreInspectResponse.AuthPreInspectData;
import org.openhab.binding.smilescloud.internal.api.dto.RegionResponse;
import org.openhab.binding.smilescloud.internal.exception.SmilesCloudAuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Authentication service for the Hoymiles S-Miles Cloud.
 * Handles region discovery, pre-inspect, Argon2id/unsalted v3 login, profile probe, and token
 * lifecycle.
 *
 * @author Florian Wagner - Initial contribution
 */
@NonNullByDefault
public class SmilesCloudAuthService {

    private final Logger logger = LoggerFactory.getLogger(SmilesCloudAuthService.class);
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final ReentrantLock authLock = new ReentrantLock();

    private volatile @Nullable String authToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;
    private volatile @Nullable String lastSuccessfulMethod;
    private volatile @Nullable String profile;
    private volatile @Nullable String regionHost;
    private volatile @Nullable Integer dataCenterMarker;

    public SmilesCloudAuthService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public @Nullable String getToken() {
        return authToken;
    }

    public boolean isTokenValid() {
        return authToken != null && Instant.now().isBefore(tokenExpiry);
    }

    public @Nullable String getProfile() {
        return profile;
    }

    public @Nullable String getRegionHost() {
        return regionHost;
    }

    public @Nullable Integer getDataCenterMarker() {
        return dataCenterMarker;
    }

    /**
     * Restore previously persisted auth state (from StorageService).
     */
    public void restoreState(@Nullable String token, @Nullable String method, @Nullable String expiryIso,
            @Nullable String savedProfile, @Nullable String savedRegionHost, @Nullable Integer savedDc) {
        if (token != null && expiryIso != null) {
            Instant expiry = Instant.parse(expiryIso);
            if (Instant.now().isBefore(expiry)) {
                this.authToken = token;
                this.tokenExpiry = expiry;
                this.lastSuccessfulMethod = method;
                this.profile = savedProfile;
                this.regionHost = savedRegionHost;
                this.dataCenterMarker = savedDc;
                logger.debug("Restored auth state, token valid until {}", expiry);
            }
        }
    }

    /**
     * Ensures a valid token is available. Re-authenticates if expired.
     */
    public void ensureAuthenticated(String baseUrl, String username, String password)
            throws SmilesCloudAuthenticationException {
        if (isTokenValid()) {
            return;
        }
        authLock.lock();
        try {
            if (isTokenValid()) {
                return;
            }
            authenticate(baseUrl, username, password);
        } finally {
            authLock.unlock();
        }
    }

    /**
     * Forces re-authentication (e.g. after an API call fails with auth error).
     */
    public void forceReauthenticate(String baseUrl, String username, String password)
            throws SmilesCloudAuthenticationException {
        authLock.lock();
        try {
            authToken = null;
            tokenExpiry = Instant.EPOCH;
            authenticate(baseUrl, username, password);
        } finally {
            authLock.unlock();
        }
    }

    /**
     * Clears all auth state.
     */
    public void clearState() {
        authToken = null;
        tokenExpiry = Instant.EPOCH;
        lastSuccessfulMethod = null;
        profile = null;
        regionHost = null;
        dataCenterMarker = null;
    }

    private void authenticate(String baseUrl, String username, String password)
            throws SmilesCloudAuthenticationException {
        String effectiveHost = discoverRegion(baseUrl, username);

        String token = loginV3(effectiveHost, username, password);
        authToken = token;
        tokenExpiry = Instant.now().plusSeconds(TOKEN_MAX_AGE_SECONDS);

        profile = probeProfile(effectiveHost, token);
        logger.debug("Auth success: profile={}, host={}, dc={}", profile, effectiveHost, dataCenterMarker);
    }

    // --- Region Discovery ---

    private String discoverRegion(String baseUrl, String username) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("email", username);
            String response = postJson(baseUrl + API_REGION_PATH, body, null);
            RegionResponse region = gson.fromJson(response, RegionResponse.class);

            if (region != null && region.isSuccess() && region.data != null) {
                RegionResponse.RegionData data = region.data;
                dataCenterMarker = data.dc;
                String loginUrl = data.loginUrl;
                if (loginUrl != null && !loginUrl.isEmpty() && loginUrl.startsWith("https://")) {
                    regionHost = loginUrl;
                    logger.debug("Region discovery: host={}, dc={}", loginUrl, data.dc);
                    return loginUrl;
                }
            }
        } catch (Exception e) {
            logger.debug("Region discovery failed (non-fatal): {}", e.getMessage());
        }
        regionHost = baseUrl;
        return baseUrl;
    }

    // --- V3 Login ---

    private String loginV3(String host, String username, String password) throws SmilesCloudAuthenticationException {
        String savedMethod = lastSuccessfulMethod;
        if (savedMethod != null) {
            try {
                return attemptLoginV3(host, username, password, savedMethod);
            } catch (SmilesCloudAuthenticationException e) {
                logger.debug("Cached auth method {} failed, trying full cascade", savedMethod);
            }
        }

        AuthPreInspectData preData = preInspect(host, username);
        String salt = preData.a;
        String nonce = preData.n;
        if (nonce == null) {
            throw new SmilesCloudAuthenticationException("Pre-inspect returned no nonce");
        }

        Integer accountState = preData.f;
        if (accountState != null && accountState == 1) {
            throw new SmilesCloudAuthenticationException("Password expired — please update in the S-Miles app");
        }

        if (salt != null && !salt.isEmpty()) {
            String ch = computeArgon2Challenge(password, salt);
            String token = submitLogin(host, username, ch, nonce);
            lastSuccessfulMethod = "argon2_v3";
            return token;
        }

        return tryUnsaltedLogin(host, username, password, nonce);
    }

    private String attemptLoginV3(String host, String username, String password, String method)
            throws SmilesCloudAuthenticationException {
        AuthPreInspectData preData = preInspect(host, username);
        String nonce = preData.n;
        if (nonce == null) {
            throw new SmilesCloudAuthenticationException("Pre-inspect returned no nonce");
        }

        String ch;
        if ("argon2_v3".equals(method)) {
            String salt = preData.a;
            if (salt == null || salt.isEmpty()) {
                throw new SmilesCloudAuthenticationException("Expected salt for argon2 but got none");
            }
            ch = computeArgon2Challenge(password, salt);
        } else if ("sha256_v3".equals(method)) {
            ch = computeSha256V3Challenge(password);
        } else if ("sha256_hex_v3".equals(method)) {
            ch = computeSha256HexChallenge(password);
        } else {
            throw new SmilesCloudAuthenticationException("Unknown auth method: " + method);
        }
        return submitLogin(host, username, ch, nonce);
    }

    private String tryUnsaltedLogin(String host, String username, String password, String nonce)
            throws SmilesCloudAuthenticationException {
        try {
            String ch = computeSha256V3Challenge(password);
            String token = submitLogin(host, username, ch, nonce);
            lastSuccessfulMethod = "sha256_v3";
            return token;
        } catch (SmilesCloudAuthenticationException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (!msg.contains("invalid credentials") && !msg.contains("log in failed")
                    && !msg.contains("check your account and password")) {
                throw e;
            }
            logger.debug("sha256_v3 failed, trying sha256_hex_v3");
        }

        AuthPreInspectData freshPre = preInspect(host, username);
        String freshNonce = freshPre.n;
        if (freshNonce == null) {
            throw new SmilesCloudAuthenticationException("Pre-inspect returned no nonce on retry");
        }
        String ch = computeSha256HexChallenge(password);
        String token = submitLogin(host, username, ch, freshNonce);
        lastSuccessfulMethod = "sha256_hex_v3";
        return token;
    }

    // --- Pre-inspect ---

    private AuthPreInspectData preInspect(String host, String username) throws SmilesCloudAuthenticationException {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("u", username);
            String response = postJson(host + API_PRE_INSPECT_PATH, body, null);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            String status = getJsonString(json, "status");
            String message = getJsonString(json, "message");
            if (!"0".equals(status)) {
                throw new SmilesCloudAuthenticationException("Pre-inspect failed (status=" + status + "): " + message);
            }

            if (!json.has("data") || !json.get("data").isJsonObject()) {
                throw new SmilesCloudAuthenticationException("Pre-inspect returned no data object");
            }
            JsonObject dataObj = json.getAsJsonObject("data");
            AuthPreInspectData data = new AuthPreInspectData();
            data.n = getJsonString(dataObj, "n");
            data.a = getJsonString(dataObj, "a");
            data.v = dataObj.has("v") && !dataObj.get("v").isJsonNull() ? dataObj.get("v").getAsInt() : null;
            data.dc = dataObj.has("dc") && !dataObj.get("dc").isJsonNull() ? dataObj.get("dc").getAsInt() : null;
            data.f = dataObj.has("f") && !dataObj.get("f").isJsonNull() ? dataObj.get("f").getAsInt() : null;
            logger.debug("Pre-inspect: v={}, saltPresent={}, dc={}, f={}", data.v, data.a != null, data.dc, data.f);
            return data;
        } catch (SmilesCloudAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            logger.debug("Pre-inspect request to {} failed: {}", host, e.getMessage(), e);
            throw new SmilesCloudAuthenticationException(
                    "Pre-inspect request failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    // --- Login submit ---

    private String submitLogin(String host, String username, String ch, String nonce)
            throws SmilesCloudAuthenticationException {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("u", username);
            body.addProperty("ch", ch);
            body.addProperty("n", nonce);
            String response = postJson(host + API_LOGIN_V3_PATH, body, null);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            String status = getJsonString(json, "status");
            String message = getJsonString(json, "message");
            if (!"0".equals(status)) {
                throw new SmilesCloudAuthenticationException("Login failed (status=" + status + "): " + message);
            }

            if (!json.has("data") || !json.get("data").isJsonObject()) {
                throw new SmilesCloudAuthenticationException("Login succeeded but no data object returned");
            }
            JsonObject dataObj = json.getAsJsonObject("data");
            String token = getJsonString(dataObj, "token");
            if (token == null || token.isEmpty()) {
                throw new SmilesCloudAuthenticationException("Login succeeded but no token returned");
            }
            return token;
        } catch (SmilesCloudAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new SmilesCloudAuthenticationException("Login request failed", e);
        }
    }

    private static @Nullable String getJsonString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    // --- Profile probe ---

    private String probeProfile(String host, String token) {
        logger.debug("Probing account profile via {}{}", host, API_PROFILE_PROBE_PATH);
        try {
            JsonObject body = new JsonObject();
            body.addProperty("page", 1);
            body.addProperty("page_size", 1);
            String response = postJson(host + API_PROFILE_PROBE_PATH, body, token);
            JsonObject result = gson.fromJson(response, JsonObject.class);
            if (result != null && "0".equals(getJsonString(result, "status"))) {
                logger.debug("Profile probe accepted → installer profile");
                return "installer";
            }
            logger.debug("Profile probe rejected (status={}) → home profile",
                    result != null ? getJsonString(result, "status") : "non-JSON");
        } catch (Exception e) {
            logger.debug("Profile probe failed (expected for home accounts): {}", e.getMessage());
        }
        logger.info("Detected S-Miles Home account profile — using _c API endpoints");
        return "home";
    }

    // --- Credential computation ---

    static String computeArgon2Challenge(String password, String hexSalt) {
        byte[] salt = HexFormat.of().parseHex(hexSalt);
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(0x13)
                .withIterations(3).withMemoryAsKB(32768).withParallelism(1).withSalt(salt).build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] hash = new byte[32];
        generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), hash);
        return HexFormat.of().formatHex(hash);
    }

    static String computeSha256V3Challenge(String password) {
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        String md5Hex = md5Hex(passwordBytes);
        String sha256B64 = Base64.getEncoder().encodeToString(sha256(passwordBytes));
        return md5Hex + "." + sha256B64;
    }

    static String computeSha256HexChallenge(String password) {
        return HexFormat.of().formatHex(sha256(password.getBytes(StandardCharsets.UTF_8)));
    }

    private static String md5Hex(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // --- HTTP helper ---

    private static final String APP_VERSION = "2.9.0";
    private static final int APP_TID = 159;

    private String getUserAgent() {
        Integer dc = dataCenterMarker;
        return "sma/ad/" + APP_VERSION + "/" + APP_TID + "/" + (dc != null ? dc : 0);
    }

    private String postJson(String url, JsonObject body, @Nullable String token)
            throws InterruptedException, TimeoutException, ExecutionException {
        String userAgent = getUserAgent();
        var request = httpClient.newRequest(url).method(HttpMethod.POST).header("Content-Type", "application/json")
                .header("Accept", "application/json").agent(userAgent).header("App-Version", APP_VERSION)
                .header("X-App-Version", APP_VERSION).timeout(HTTP_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .content(new StringContentProvider(gson.toJson(body)));
        if (token != null) {
            request.header("Authorization", token);
        }
        ContentResponse response = request.send();
        String responseBody = response.getContentAsString();
        logger.debug("POST {} → HTTP {} (UA: {})", url, response.getStatus(), userAgent);
        logger.trace("Response body: {}", responseBody);
        return responseBody;
    }
}
