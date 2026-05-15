# openHAB S-Miles Cloud Binding — Implementation Plan

## Overview

An openHAB binding that connects to the Hoymiles S-Miles Cloud (`neapi.hoymiles.com`) to read
real-time data from solar inverters. Modeled after the Home Assistant integrations
(Philra94/homeassistant-hoymiles-cloud, wil-lem/ha-hoymiles-s-cloud) and the ioBroker adapter
(Eistee82/ioBroker.hoymiles).

Phase 1 targets **Home profile** accounts only (S-Miles Home app users) using the
`/pvmc/..._c` API surface. Installer/Web profile support may follow in a later phase.

## Architecture

```text
Bridge: SmilesCloudAccount
  ├── config: username, password, pollingInterval (baseUrl advanced)
  ├── handles: region discovery, authentication (v3 Argon2 + unsalted fallbacks),
  │            token lifecycle, profile probe
  ├── owns: single HttpClient, API client, auth service, StorageService for tokens
  ├── polling: scheduler drives all API calls, pushes data to child handlers
  └── discovery: auto-discovers stations after login

Thing: SmilesCloudStation
  ├── config: stationId
  ├── static channels: station-level aggregate data (power, energy, grid, battery)
  ├── dynamic channels: per-PV-string V/I/P (created after first data fetch)
  └── writable channel: powerLimit (5–100%)
```

### Data Flow

1. Bridge poll fires on `pollingInterval` schedule
1. Bridge calls `getStationRealtime(sid)` and `getPvIndicators(sid)` for each registered station
1. Bridge stores results in `ConcurrentHashMap<String, StationData>`
1. Bridge iterates child station handlers and calls `handler.updateData(dto)`
1. Station handler updates channel states from the DTO

Station handlers never call HTTP directly — all API access goes through the bridge.

## API Protocol

All endpoints are **POST** with JSON body. Base URL is `https://neapi.hoymiles.com` (default).

### Response Envelope

Every API response uses HTTP 200 with a JSON envelope:

```json
{
  "status": "0",
  "message": "success",
  "data": { ... }
}
```

- Success: `status == "0"` AND `message == "success"`
- Auth error: `status != "0"`, message contains error reason (e.g., `"invalid credentials"`,
  `"version is low"`, `"check your account and password"`)
- No HTTP-level error codes are used (no 401, 429, 500) — all errors are in the JSON body

### API Endpoints

| Purpose | Path | Payload | Profile |
|---|---|---|---|
| Region discovery | `/iam/pub/0/c/region_c` | `{email}` | N/A (pre-auth) |
| Pre-inspect v3 | `/iam/pub/3/auth/pre-insp` | `{u}` | N/A (pre-auth) |
| Login v3 | `/iam/pub/3/auth/login` | `{u, ch, n}` | N/A (pre-auth) |
| Profile probe | `/pvm/api/0/station/select_by_page` | `{page, page_size}` | Determines profile |
| List stations | `/pvmc/api/0/station/select_by_page_c` | `{page, page_size}` | Home |
| Station detail | `/pvmc/api/0/station/find_c` | `{sid}` | Home |
| Real-time data | `/pvmc/api/0/station_data/count_station_real_data_c` | `{sid}` | Home |
| PV indicators | `/pvm-data/api/0/indicators/data/select_real_indicators_data` | `{sid, type: 4}` | Both |
| Device tree | `/pvmc/api/0/station/select_device_c` | `{sid}` | Home |
| Power limit | `/pvm-ctl/api/0/dev/command/put` | `{action:8, data:{sid, power_limit, enable:1}}` | Both |

### Response Shapes

#### Station List (`select_by_page_c`)

```json
{
  "status": "0",
  "message": "success",
  "data": {
    "list": [
      {"sid": 12345, "name": "My Station", ...}
    ],
    "total": 1
  }
}
```

Note: Home profile uses `sid` instead of `id` for the station identifier. Normalize to `id`
internally.

#### Real-time Data (`count_station_real_data_c`)

```json
{
  "status": "0",
  "message": "success",
  "data": {
    "real_power": "1234.5",
    "today_eq": "5678",
    "month_eq": "123456",
    "year_eq": "1234567",
    "total_eq": "12345678",
    "co2_emission_reduction": "9876",
    "plant_tree": "5",
    "data_time": "2026-05-15 14:30:00",
    "capacitor": "6000",
    "reflux_station_data": {
      "grid_power": "100.5",
      "load_power": "800.0",
      "bms_power": "200.0",
      "bms_soc": "75",
      "meter_b_in_eq": "1000",
      "meter_b_out_eq": "2000",
      "bms_in_eq": "500",
      "bms_out_eq": "300",
      "use_eq_total": "4500",
      "pv_to_load_eq": "3000",
      "mb_in_eq": {"total_eq": "50000"},
      "mb_out_eq": {"total_eq": "80000"},
      "inv_num": "2"
    }
  }
}
```

All numeric values are **strings** in the API response. Parse with null/empty/"-" handling.

#### PV Indicators (`select_real_indicators_data`)

```json
{
  "status": "0",
  "message": "success",
  "data": {
    "list": [
      {"key": "1_pv_v", "val": "32.5"},
      {"key": "1_pv_i", "val": "8.2"},
      {"key": "1_pv_p", "val": "266.5"},
      {"key": "2_pv_v", "val": "33.1"},
      {"key": "2_pv_i", "val": "7.9"},
      {"key": "2_pv_p", "val": "261.5"},
      {"key": "pv_p_total", "val": "528.0"}
    ]
  }
}
```

PV channel count is discovered from the key prefixes (`{N}_pv_{v|i|p}`). No hardcoded max.

#### Pre-inspect (`pre-insp`)

```json
{
  "status": "0",
  "message": "success",
  "data": {
    "n": "<nonce>",
    "a": "<hex_salt_or_null>",
    "v": 3,
    "dc": 0,
    "f": 0,
    "t": 159
  }
}
```

- `a` present (non-null): use Argon2id
- `a` null/absent: use unsalted credential challenges
- `v`: auth protocol version (3 = Argon2 era, 2 = legacy; informational only — not used as
  profile signal since 2026 Hoymiles migration)
- `f`: account state (0 = normal, 1 = password expired, 2 = email-add required)

#### Region Discovery (`region_c`)

```json
{
  "status": "0",
  "message": "success",
  "data": {
    "login_url": "https://euapi.hoymiles.com",
    "dc": 1
  }
}
```

- `login_url`: regional API host for auth endpoints
- `dc`: data center marker, included in User-Agent

## Authentication

### Flow

```text
1. POST /iam/pub/0/c/region_c {email}
   → discover regional host + dc marker (non-fatal on failure, fall back to default)

1. POST /iam/pub/3/auth/pre-insp {u: email}
   → {n: nonce, a: salt_or_null, v, dc, f, t}

1. Compute credential challenge:
   a. If a != null (salt present) → Argon2id(password, hex_decode(salt), t=3, m=32768, p=1, hashLen=32).hex()
   b. If a == null → try unsalted variants in order:
      i.  sha256_v3: md5(password).hex() + "." + base64(sha256(password))
      ii. sha256_hex_v3: sha256(password).hex()
      (each variant needs a fresh pre-insp call for a new nonce)

1. POST /iam/pub/3/auth/login {u: email, ch: credential_hash, n: nonce}
   → {data: {token: "..."}}

1. POST /pvm/api/0/station/select_by_page {page:1, page_size:1}
   → if status=0: profile=installer; else: profile=home

1. Store token + auth method + profile in StorageService
```

### Client Profiles (Phase 1: Web + Home only)

| Profile | User-Agent | App-Version | X-Client-Type |
|---|---|---|---|
| Web | `HomeAssistant-HoymilesCloud` (or `openHAB-SmilesCloud`) | (none) | (none) |
| Home | `sma/ad/<version>/<tid>/<dc>` | `2.9.0` | (none, set via UA) |

Auth endpoints use the Web profile headers. Once the profile probe determines the account
is "home", subsequent data API calls use the Home profile User-Agent.

### Token Lifecycle

- Token TTL is **not returned by the server**. Use client-managed expiry: **1 hour** (conservative;
  actual TTL is ~2 hours based on reference implementations).
- On each API call, check `Instant.now().isAfter(tokenExpiry)` → re-authenticate if expired.
- On any API call that returns a non-success status suggesting auth failure → re-authenticate
  and retry once.
- **Persistence:** Store in `StorageService` (keyed by thing UID):
  - `authToken`: the raw token string
  - `authMethod`: which credential variant succeeded (e.g., `"argon2_v3"`, `"sha256_v3"`)
  - `tokenExpiry`: ISO-8601 instant
  - `profile`: `"home"` or `"installer"`
  - `regionHost`: discovered regional base URL
  - `dc`: data center marker
- On openHAB restart, restore token from storage. If not expired, skip re-auth.
- On re-auth after token expiry, try the last-successful auth method first, then fall through
  to the full cascade if it fails. This avoids unnecessary Argon2 computations.

### Thread Safety

Token access must be thread-safe since multiple station handlers may trigger API calls
concurrently:

```java
private final ReentrantLock authLock = new ReentrantLock();
private volatile @Nullable String authToken;
private volatile Instant tokenExpiry = Instant.EPOCH;

private void ensureAuthenticated() throws SmilesCloudAuthenticationException {
    if (Instant.now().isBefore(tokenExpiry)) {
        return; // token still valid
    }
    authLock.lock();
    try {
        if (Instant.now().isBefore(tokenExpiry)) {
            return; // another thread already refreshed
        }
        authenticate();
    } finally {
        authLock.unlock();
    }
}
```

### Secure Disposal

```java
@Override
public void handleRemoval() {
    Storage<String> storage = storageService.getStorage(thing.getUID().toString());
    storage.remove("authToken");
    storage.remove("authMethod");
    storage.remove("tokenExpiry");
    storage.remove("profile");
    storage.remove("regionHost");
    storage.remove("dc");
    super.handleRemoval();
}
```

## Argon2 Library: Bouncy Castle (pure Java)

Use `bcprov-jdk18on` version 1.84 (same as boschshc, homekit, icloud, androidtv bindings).

```xml
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcprov-jdk18on</artifactId>
  <version>1.84</version>
  <scope>compile</scope>
</dependency>
```

Argon2 parameters (hardcoded — server does not return these; matches S-Miles Home app
`Argon2IDUtil.kt`):

| Parameter | Value |
|---|---|
| Type | Argon2id |
| Version | 0x13 |
| Time cost | 3 |
| Memory cost | 32768 (32 MiB) |
| Parallelism | 1 |
| Hash length | 32 bytes |
| Salt | hex-decoded from `pre-insp.a` |
| Password | UTF-8 bytes |
| Output | hex-encoded |

The ~1.2s computation happens only at login (every ~1 hour), not per polling cycle.

### Cross-language verification

```text
Password: testpassword123
Salt: d5e3f019748d7a36d69840fdfd873d15
Java (Bouncy Castle): 3c5d1ece590f242aa94b901f3940ebfd89b7bd0fdd21132a69e4321d6436a409
Python (argon2-cffi): 3c5d1ece590f242aa94b901f3940ebfd89b7bd0fdd21132a69e4321d6436a409
```

## Security

### Password Handling

- Bridge config parameter uses `<context>password</context>` for UI masking
- Password is never stored in plaintext beyond the config — only hashed on the wire
- Config POJO `toString()` redacts password: `password=***`

### Token Handling

- Token stored in `StorageService` (internal openHAB storage), not in thing config
- Token never logged at any level — use `"token present: {}"` / `"token absent"` pattern
- Authorization header set as raw token (no Bearer prefix) per API spec:
  `request.header("Authorization", token)`
- Token cleared from storage on thing removal (`handleRemoval()`)

### Credential Logging Prevention

- Auth service logs only method names and success/failure, never credentials or token values
- API client logs request URLs and response status codes, never Authorization header values
- Config `toString()` redacts password
- Debug-level logs for response bodies must redact any `token` fields

### HTTPS Enforcement

- `baseUrl` must start with `https://`. Reject in `initialize()` with
  `CONFIGURATION_ERROR` if not.
- Regional host from `region_c` is also validated for HTTPS scheme.

### MD5/SHA-256 Usage Note

MD5 and SHA-256 are used here as **protocol-mandated credential transforms** (the API requires
these specific hash formats), not for password storage. This is the same pattern used by
`org.openhab.binding.mideaac`, `org.openhab.binding.meross`, and many others in this repo.

## Channels

### Station-level Static Channels

All channels are `readOnly="true"` unless noted. All numeric values from the API are strings;
parse with null/empty/"-" handling.

| Channel ID | Item Type | Unit | API Field | State Class | Notes |
|---|---|---|---|---|---|
| pvPower | Number:Power | W | real_power | MEASUREMENT | |
| todayEnergy | Number:Energy | Wh | today_eq | TOTAL_INCREASING | |
| monthEnergy | Number:Energy | Wh | month_eq | TOTAL_INCREASING | |
| yearEnergy | Number:Energy | Wh | year_eq | TOTAL_INCREASING | |
| totalEnergy | Number:Energy | Wh | total_eq | TOTAL_INCREASING | |
| co2Reduction | Number:Mass | g | co2_emission_reduction | TOTAL_INCREASING | |
| gridPower | Number:Power | W | reflux_station_data.grid_power | MEASUREMENT | Battery stations only |
| loadPower | Number:Power | W | reflux_station_data.load_power | MEASUREMENT | Battery stations only |
| batteryPower | Number:Power | W | reflux_station_data.bms_power | MEASUREMENT | Battery stations only |
| batterySoc | Number:Dimensionless | % | reflux_station_data.bms_soc | MEASUREMENT | Battery stations only |
| gridImportToday | Number:Energy | Wh | reflux_station_data.meter_b_in_eq | TOTAL_INCREASING | |
| gridExportToday | Number:Energy | Wh | reflux_station_data.meter_b_out_eq | TOTAL_INCREASING | |
| batteryChargeToday | Number:Energy | Wh | reflux_station_data.bms_in_eq | TOTAL_INCREASING | Battery only |
| batteryDischargeToday | Number:Energy | Wh | reflux_station_data.bms_out_eq | TOTAL_INCREASING | Battery only |
| consumptionToday | Number:Energy | Wh | reflux_station_data.use_eq_total | TOTAL_INCREASING | |
| pvToLoadToday | Number:Energy | Wh | reflux_station_data.pv_to_load_eq | TOTAL_INCREASING | |
| gridImportTotal | Number:Energy | Wh | reflux_station_data.mb_in_eq.total_eq | TOTAL_INCREASING | |
| gridExportTotal | Number:Energy | Wh | reflux_station_data.mb_out_eq.total_eq | TOTAL_INCREASING | |
| lastUpdate | DateTime | — | data_time | — | Diagnostic |
| powerLimit | Number:Dimensionless | % | command/put | — | **Writable**, 5–100 |

### Null vs Zero vs UNDEF Semantics

| Condition | Channel State |
|---|---|
| API returns `"0"` or `"0.0"` | `QuantityType(0, unit)` — valid reading (e.g. night) |
| API returns `null`, `""`, or `"-"` | `UnDefType.NULL` — field known absent |
| API call fails / no data | `UnDefType.UNDEF` — no data available |
| Battery field absent (non-hybrid station) | Channel not created (or `UnDefType.UNDEF`) |

### Battery Channel Availability

Battery/grid channels (`gridPower`, `loadPower`, `batteryPower`, `batterySoc`,
`batteryChargeToday`, `batteryDischargeToday`) are detected at runtime by checking whether
any of `bms_power`, `bms_soc`, `bms_in_eq`, `bms_out_eq` are non-null/non-empty in
`reflux_station_data`. If absent, these channels report `UnDefType.UNDEF`.

### Dynamic PV String Channels

Created at runtime after the first `select_real_indicators_data` response. PV channel count is
discovered from key patterns `{N}_pv_{v|i|p}`.

| Channel ID Pattern | Item Type | Unit | API Key |
|---|---|---|---|
| pv{N}Voltage | Number:ElectricPotential | V | {N}_pv_v |
| pv{N}Current | Number:ElectricCurrent | A | {N}_pv_i |
| pv{N}Power | Number:Power | W | {N}_pv_p |
| pvTotalPower | Number:Power | W | pv_p_total |

Implementation uses **Option A** (pre-defined channel types in XML, instances created at
runtime with `ChannelBuilder`):

```java
// After first PV indicator fetch reveals N PV strings:
List<Channel> channels = new ArrayList<>(getThing().getChannels());
for (int i : discoveredPvChannels) {
    channels.add(ChannelBuilder
        .create(new ChannelUID(thing.getUID(), "pv" + i + "Voltage"), "Number:ElectricPotential")
        .withType(new ChannelTypeUID(BINDING_ID, "pvVoltage"))
        .withLabel("PV" + i + " Voltage")
        .build());
    // ... current, power ...
}
updateThing(editThing().withChannels(channels).build());
```

### `lastUpdate` Timestamp

API field `data_time` is a **naive local datetime** string: `"2026-05-15 14:30:00"`.
The station's timezone is available from station details (`timezone.tz_name`, `timezone.offset`).

Parsing:

```java
LocalDateTime naive = LocalDateTime.parse(dataTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
ZonedDateTime zoned = naive.atZone(stationTimeZone); // or system default if unknown
updateState(channelUID, new DateTimeType(zoned));
```

### Power Limit (Writable Channel)

- Valid range: **5–100%** (integer)
- Clamped on write: `min(100, max(5, value))`
- Endpoint: `POST /pvm-ctl/api/0/dev/command/put`
- Payload: `{"action": 8, "data": {"sid": <stationId>, "power_limit": <value>, "enable": 1}}`
- Response: standard JSON envelope; check `status == "0"`
- Channel type has `min="5"` and `max="100"` in state description

## Configuration

### Bridge Config (`SmilesCloudAccountConfig`)

```xml
<bridge-type id="account">
    <label>S-Miles Cloud Account</label>
    <description>Connection to a Hoymiles S-Miles Cloud account</description>
    <config-description>
        <parameter name="username" type="text" required="true">
            <label>Username</label>
            <description>Email address of the S-Miles Cloud account</description>
            <context>email</context>
        </parameter>
        <parameter name="password" type="text" required="true">
            <label>Password</label>
            <description>Password of the S-Miles Cloud account</description>
            <context>password</context>
        </parameter>
        <parameter name="pollingInterval" type="integer" min="60" unit="s">
            <label>Polling Interval</label>
            <description>Seconds between data updates (minimum 60)</description>
            <default>120</default>
        </parameter>
        <parameter name="baseUrl" type="text" required="false">
            <label>API Base URL</label>
            <description>Base URL of the S-Miles Cloud API (advanced)</description>
            <default>https://neapi.hoymiles.com</default>
            <advanced>true</advanced>
        </parameter>
    </config-description>
</bridge-type>
```

### Station Config (`SmilesCloudStationConfig`)

```xml
<thing-type id="station" listed="false">
    <supported-bridge-type-refs>
        <bridge-type-ref id="account"/>
    </supported-bridge-type-refs>
    <label>S-Miles Cloud Station</label>
    <description>A solar station monitored via the S-Miles Cloud</description>
    <representation-property>stationId</representation-property>
    <config-description>
        <parameter name="stationId" type="text" required="true">
            <label>Station ID</label>
            <description>The station ID from the S-Miles Cloud</description>
        </parameter>
    </config-description>
</thing-type>
```

### `addon.xml`

```xml
<addon:addon id="smilescloud">
    <type>binding</type>
    <name>S-Miles Cloud Binding</name>
    <description>Connects to the Hoymiles S-Miles Cloud for solar inverter monitoring</description>
    <connection>cloud</connection>
</addon:addon>
```

### Config POJO

```java
@NonNullByDefault
public class SmilesCloudAccountConfig {
    public String username = "";
    public String password = "";
    public int pollingInterval = 120;
    public String baseUrl = "https://neapi.hoymiles.com";

    @Override
    public String toString() {
        return "SmilesCloudAccountConfig[username=" + username
            + ", password=" + (password.isEmpty() ? "<empty>" : "***")
            + ", pollingInterval=" + pollingInterval
            + ", baseUrl=" + baseUrl + "]";
    }
}
```

### Validation in `initialize()`

1. `username` must be non-blank
1. `password` must be non-blank
1. `baseUrl` must start with `https://`
1. `pollingInterval` must be >= 60
1. Failures → `OFFLINE` + `CONFIGURATION_ERROR` with message

### Runtime Config Changes

Override `handleConfigurationUpdate()` to dispose and re-initialize on credential/URL changes:

```java
@Override
public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
    super.handleConfigurationUpdate(configurationParameters);
    dispose();
    initialize();
}
```

## Error Handling

| Scenario | Bridge Status | Station Status | Action |
|---|---|---|---|
| Wrong credentials | OFFLINE + CONFIGURATION_ERROR | OFFLINE + BRIDGE_OFFLINE | Don't retry — user must fix config |
| `pre-insp` says f=1 (password expired) | OFFLINE + CONFIGURATION_ERROR | OFFLINE + BRIDGE_OFFLINE | Don't retry |
| Transient auth failure (network) | OFFLINE + COMMUNICATION_ERROR | OFFLINE + BRIDGE_OFFLINE | Retry on next poll |
| Token expired | Stay ONLINE | Stay ONLINE | Re-auth transparently, continue poll |
| API non-success during data fetch | Stay ONLINE | OFFLINE + COMMUNICATION_ERROR | Retry on next poll |
| Data fetch network error/timeout | Stay ONLINE | OFFLINE + COMMUNICATION_ERROR | Retry on next poll |
| Station not in API response | ONLINE | OFFLINE + CONFIGURATION_ERROR | Station ID invalid |
| `region_c` fails | ONLINE (proceed with default host) | — | Non-fatal, use neapi.hoymiles.com |
| HTTPS validation fails | OFFLINE + CONFIGURATION_ERROR | — | User must fix baseUrl |

### Auth Error Classification

From the API error messages (reference: Philra94 `classify_auth_failure`):

| Message pattern | Classification |
|---|---|
| `"version is low"` or `"update to the latest version"` | App version rejected |
| `"s-miles home"` | Wrong client profile |
| `"invalid credentials"` / `"check your account and password"` | Wrong password |
| Other non-zero status | Generic auth error |

## Polling

```java
// In bridge initialize():
pollingJob = scheduler.scheduleWithFixedDelay(this::poll, 0, config.pollingInterval, TimeUnit.SECONDS);

// In bridge dispose():
ScheduledFuture<?> job = pollingJob;
if (job != null) {
    job.cancel(true);
    pollingJob = null;
}
```

- Minimum interval: 60 seconds (enforced in config validation)
- Default interval: 120 seconds
- The three API calls per station (real-time, PV indicators, optionally station details) are
  made sequentially within a single poll cycle, not concurrently, to be gentle on the API
- HTTP request timeout: 30 seconds per request

## Discovery

```java
public class SmilesCloudStationDiscoveryService
        extends AbstractThingHandlerDiscoveryService<SmilesCloudAccountHandler> {

    private static final int DISCOVERY_TIMEOUT_SECONDS = 10;

    @Override
    protected void startScan() {
        SmilesCloudAccountHandler bridge = getThingHandler();
        Map<String, String> stations = bridge.getDiscoveredStations();
        for (Map.Entry<String, String> entry : stations.entrySet()) {
            ThingUID bridgeUID = bridge.getThing().getUID();
            ThingUID thingUID = new ThingUID(THING_TYPE_STATION, bridgeUID, entry.getKey());
            DiscoveryResult result = DiscoveryResultBuilder.create(thingUID)
                .withBridge(bridgeUID)
                .withLabel(entry.getValue())
                .withProperty("stationId", entry.getKey())
                .withRepresentationProperty("stationId")
                .build();
            thingDiscovered(result);
        }
    }
}
```

Background discovery runs after each successful station list fetch (in the poll cycle).

## Project Structure

```text
bundles/org.openhab.binding.smilescloud/
├── pom.xml
├── README.md
├── PLAN.md
├── NOTICE
├── src/main/
│   ├── feature/feature.xml
│   ├── java/org/openhab/binding/smilescloud/internal/
│   │   ├── SmilesCloudBindingConstants.java
│   │   ├── SmilesCloudHandlerFactory.java
│   │   ├── handler/
│   │   │   ├── SmilesCloudAccountHandler.java       (bridge)
│   │   │   └── SmilesCloudStationHandler.java       (thing)
│   │   ├── api/
│   │   │   ├── SmilesCloudApiClient.java            (HTTP calls + response parsing)
│   │   │   ├── SmilesCloudAuthService.java          (auth flows + token management)
│   │   │   └── dto/
│   │   │       ├── ApiResponse.java                 (generic envelope)
│   │   │       ├── AuthPreInspectResponse.java
│   │   │       ├── AuthLoginResponse.java
│   │   │       ├── RegionResponse.java
│   │   │       ├── StationListResponse.java
│   │   │       ├── StationRealTimeData.java
│   │   │       └── PvIndicatorsData.java
│   │   ├── config/
│   │   │   ├── SmilesCloudAccountConfig.java
│   │   │   └── SmilesCloudStationConfig.java
│   │   ├── discovery/
│   │   │   └── SmilesCloudStationDiscoveryService.java
│   │   └── exception/
│   │       ├── SmilesCloudAuthenticationException.java
│   │       └── SmilesCloudApiException.java
│   └── resources/OH-INF/
│       ├── addon/addon.xml
│       ├── i18n/smilescloud.properties
│       ├── thing/bridge.xml
│       ├── thing/station.xml
│       └── thing/channel-types.xml
└── src/test/java/org/openhab/binding/smilescloud/internal/
    ├── api/
    │   ├── SmilesCloudAuthServiceTest.java
    │   ├── SmilesCloudApiClientTest.java
    │   └── dto/
    │       └── ResponseParsingTest.java
    └── handler/
        └── SmilesCloudStationHandlerTest.java
```

## Test Plan

### Unit Tests (no network required)

#### Auth Hash Computation

1. Argon2id with known salt → verify hex output matches cross-language reference
1. Argon2id with empty password → verify no crash, produces valid hash
1. Argon2id with long password (>1000 chars) → verify no crash
1. Salt decoding: valid hex → correct bytes
1. Salt decoding: odd-length hex → graceful error
1. Salt decoding: non-hex characters → graceful error
1. Unsalted sha256_v3: `md5(pw).hex() + "." + base64(sha256(pw))` → verify format
1. Unsalted sha256_hex_v3: `sha256(pw).hex()` → verify format

#### API Response Parsing

1. Parse station list → extract id/name map, handle `sid` vs `id`
1. Parse real-time data → extract all numeric channel values as correct types
1. Parse PV indicators → discover channel count, extract V/I/P values
1. Parse PV indicators with 0 channels → empty list, no crash
1. Parse error response (status != "0") → exception with message
1. Parse null/empty/"-" numeric fields → null, not crash
1. Parse `data_time` → correct `ZonedDateTime` in station timezone
1. Parse `data_time` with null → null
1. Parse reflux_station_data absent → battery fields are null
1. Parse reflux_station_data present → all battery fields extracted
1. Parse region_c response → extract login_url and dc
1. Parse HTML error page → graceful error (not JSON)
1. Parse truncated JSON → graceful error
1. Parse empty body → graceful error

#### Auth Flow Orchestration (mocked HTTP)

1. Pre-insp returns salt → Argon2 path → login success
1. Pre-insp returns a=null → unsalted sha256_v3 succeeds
1. Pre-insp returns a=null → sha256_v3 fails → sha256_hex_v3 succeeds (fresh nonce)
1. All v3 variants fail → proper error reporting (no v0 fallback)
1. Pre-insp returns f=1 (password expired) → CONFIGURATION_ERROR
1. Login returns "version is low" → classified as app_update_required
1. Token expiry → re-auth triggered → success
1. Concurrent token refresh → only one auth call made (ReentrantLock)
1. Re-auth uses last-successful method first
1. Region discovery returns regional host → auth uses that host
1. Region discovery fails → auth proceeds with default host

#### Station Handler

1. Channel state updates from parsed real-time data
1. Dynamic PV channel creation on first data fetch
1. Dynamic PV channel creation with 0 PV strings → no channels added
1. Null/missing data → UNDEF state
1. Zero values → QuantityType(0, unit), not UNDEF
1. Power limit command → correct API payload built, clamped 5–100
1. Power limit command with value < 5 → clamped to 5
1. Power limit command with value > 100 → clamped to 100
1. Bridge goes OFFLINE → station goes OFFLINE with BRIDGE_OFFLINE

### Integration Tests (mocked HTTP server)

1. Full poll cycle: mock returns station list + real-time + PV indicators → bridge ONLINE,
    station discovered, all channels populated with correct values
1. Token expiry mid-cycle: first data call returns auth error → re-auth → retry → success
1. Network timeout: mock delays > 30s → station OFFLINE + COMMUNICATION_ERROR → next poll
    succeeds → station ONLINE
1. Station removed from API: mock returns empty station list → station OFFLINE +
    CONFIGURATION_ERROR
1. Malformed response: mock returns invalid JSON → graceful error, station OFFLINE
1. PV string change: first poll has 2 strings, second poll has 3 → new channel added

## Phases

### Phase 1 (MVP) — This Plan

- Bridge with region discovery + v3 auth (Argon2 + unsalted fallbacks), no v0
- Home profile only (Web profile for auth, Home profile for data API)
- Station thing with all channels listed above
- Dynamic PV string channels
- Station discovery
- Power limit control (writable channel, 5–100%)
- Battery/grid channels (conditionally available)
- Token persistence via StorageService
- Credential logging prevention
- HTTPS enforcement
- Thread-safe token access
- Secure disposal on removal
- Unit + integration tests

### Phase 2

- Installer profile support (different API endpoints: `/pvm/...`)
- Microinverter as child Things (serial, model, firmware as properties)
- Battery mode control (Select channel)
- Battery reserve SOC (Number channel, writable)
- DTU online/offline binary sensor
- Station properties (GPS, address, timezone, capacity)

### Phase 3

- Battery schedule editor (Economy/Time-of-Use)
- Peak shaving settings
- Firmware update status
- Weather data (from EU API server)
