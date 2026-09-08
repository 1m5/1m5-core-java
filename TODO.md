# 1M5 Core — TODO

Phased roadmap. Everything here is scoped to `1m5-core-java` unless a section says
otherwise. See [`DESIGN.md`](DESIGN.md) for the architecture each item builds
toward, and `1m5-docs/TODO.md` for where these milestones sit in the program
roadmap.

---

## P0 — Skeleton (done)

- [x] Rename to `network.onemfive:1m5-core:0.1.0`, package `network.onemfive.core`,
      Java 17 target.
- [x] `pom.xml`: `service-bus:1.5.0` (bringing `seda-bus:1.3.1` + `common` transitively)
      plus direct pins `common:1.3.0` and `did:1.3.0`, and `i2p:1.7.1` /
      `tor-client:1.2.1` behind config flags.
- [x] `service-bus-java` upgraded to carry the generic service-management weight
      (Class-based API, discovery, `awaitRunning`, pause/restart, reusable `Daemon`);
      `seda-bus-java` retargeted to Java 11. `1m5-core` consumes both.
- [x] `Core` = thin runtime handle (bus + ManCon state); `Daemon` extends
      `ra.servicebus.Daemon` and only fills the 1M5 hooks (incl. the `1m5.pass`
      config-or-env resolution + warning).
- [x] Service taxonomy: `CoreService` / `BusinessService` / `DataService` /
      `ProtocolService` + `Transport`, `ServiceType`. Protocol adapters discovered
      via `serviceBus.findRunningServices(ProtocolService.class)`.
- [x] ManCon model ported: `ManCon`, `ManConStatus` (instance, not statics),
      `ManConStatusListener`, `SituationalAwareness`.
- [x] `RoutingService` skeleton — picks the first ready `ProtocolService`, pushes one
      `SEND` hop, applies ManCon delay/copy parameters.
- [x] `IdentityService` — real secp256k1 / BIP-340 node identity via `did-java`
      `ra.did.nostr`, sealed at rest, stable across restart, `signAsNode`,
      `GET_NODE_IDENTITY` over the bus. (Was a placeholder skeleton in P0; delivered
      in P2 scope — see below.)
- [x] `PeerDirectory` skeleton.
- [x] `CoreSmokeTest`, `RoutingServiceTest`, `ProtocolIntegrationTest` — green.
- [x] Rewrite `README.md`, `DESIGN.md`; drop stale `RELEASE-NOTES.md`, `BUILD.md`,
      `ops/`.

---

## P1 — Router

### Stage 1a — skeleton hardening + the 2 stack defects

- [x] Fix **`ManConStatus.select()` always returns `NONE`**: `maxAvailable` is
      driven from ready transports (`RoutingService.refreshAvailability()` →
      `ManConNetworks.maxAvailableFor`), `ManConStatusListener`s fire on change,
      and `meetsFloor(...)` lets the router hold rather than downgrade.
- [x] Depend on `ra-common-java 1.3.2` (`BaseRoute.fromMap` `routedId` typo +
      `TextMessage` toMap/fromMap).
- [ ] Set `ServiceLevel` on `DataService` channels (`AtLeastOnce`/`ExactlyOnce`) so
      a crash mid routing-slip does not silently drop the envelope.
- [ ] Tolerate envelopes arriving mid-startup (or consume the `service-bus`
      readiness gate once it lands).
- [x] Router tests: `EscalationRouterTest` (12), `ManConStatusTest` (5),
      `RoutingServiceEscalationTest` (4). Full core suite 28 green.
- [ ] Remaining verification tests: `SlipJsonRoundTripTest` (`routeId` post-1.3.2),
      `ServiceTaxonomyTest`, `IdentityOverBusTest` (sealed/plaintext/upgrade/
      wrong-pass), `DaemonLifecycleTest`, `CoreClientContractTest`.

### Stage 1b — `Sender`-parity router — DONE

`RoutingService` rewritten around the pure `EscalationRouter` decision engine:

- [x] ManCon × (web | P2P) decision matrix (`ManConNetworks.acceptable`).
- [x] Address-matched transport selection: send over the transport the peer has
      an address on *and* that is currently ready (`PeerDirectory`).
- [x] Cross-transport relay fallback (peer on a blocked acceptable network + a
      relay peer on a ready one) → `RelayedExternalRoute` hop.
- [x] Message hold-queue + `RetryStrategy` backoff (20s → 1h, 24h give-up;
      `RetryScheduler` seam for tests) → dead-letter when exhausted.
- [ ] Inbound dedupe (belongs on the inbound pipeline; not the escalation path).
- [ ] Real peer store: add `resolvingarchitecture:network-manager`, back
      `PeerDirectory` with `ra.networkmanager.InMemoryPeerDB` / `P2PRelationship`
      (ack latency, reliability scoring, relationship graph).
- [ ] Live two-node relay testing over real I2P / Tor.

### Later — full `CRNetworkManagerService` ladder

- [x] Network selection by ManCon: LOW/MEDIUM → I2P/Tor by address; HIGH →
      I2P→Tor; VERYHIGH → I2P only (Tor dropped); EXTREME/NEO → non-internet.
- [x] The Bluetooth/WiFi/Satellite/FSRadio/LiFi tail of the acceptable-network
      ladder (relay reaches them).
- [x] `maxAvailable` reflects the ladder (non-internet→NEO, I2P→VERYHIGH,
      Tor→HIGH, clearnet→LOW).
- [ ] Random delays that *ratchet* with ManCon beyond the current fixed
      VERYHIGH/EXTREME/NEO parameter bands; NEO long-delay + mnemonic-only key.
- [ ] Per-*level* connectivity probing (the current `maxAvailable` is a
      transport-class heuristic, not timestamped per-level tests).
- [ ] **Jurisdiction default for `minRequired`**: copy
      `1m5-docs/jurisdictions-levels.txt` in as a resource; add
      `ManConStatus.defaultFor(String iso2)` (parse the file, `*` = fallback);
      the `Daemon` / host sets `minRequired` from it. User→jurisdiction lookup is
      the host's job. Test against a few known rows (NO→LOW, GB→MEDIUM, US→HIGH,
      CN→EXTREME, unknown→HIGH).

---

## P2 — Identity (ADR-0002)

**Delivered via `did-java` / `did-ts` / `did-vectors`:**

- [x] Real secp256k1 (ACINQ `secp256k1-kmp` JNI); x-only public-key derivation;
      hex / `npub` / `did:nostr`.
- [x] BIP-340 Schnorr sign/verify; SHA-256 event ids; NIP-01 canonical event
      serialization; field validation with positive + negative test vectors;
      `did-vectors` conformance suite as a test gate.
- [x] Node identity: stable across restart; `signAsNode`; `GET_NODE_IDENTITY`
      (status only) over the bus.
- [x] Private node secret encrypted at rest (`NostrIdentityStore`, Argon2id +
      AES-256-GCM); `1m5.pass` config-or-env; plaintext→sealed upgrade; never
      logged.
- [x] Legacy OpenPGP (`ra.did.openpgp`) kept read-only. No migration code path
      (1M5 has no user identities to migrate).
- [x] Attestations (kind 30100), guardian recovery / rotation records
      (kinds 30101–30103).

**Still open:**

- [ ] User identities distinct from the node identity; multiple scoped identities.
- [ ] Full key-domain separation (messaging / encryption / wallet / transport /
      device / session) enforced in code.
- [ ] Contact model + trust states (`unverified`, `verified_by_qr`,
      `verified_by_openpgp_migration`, `verified_by_existing_conversation`,
      `rotated`, `blocked`, `compromised`).
- [ ] E2EE envelope: secp256k1 ECDH + HKDF-SHA256 + an AEAD (ChaCha20-Poly1305 /
      AES-GCM); session keys; replay protection; wrong-recipient / tampered /
      replay tests.
- [ ] Threat-model the identity + encryption design before calling it stable;
      invite cryptographic review.

---

## Embedding contract — `CoreClient` (in this module)

Package `network.onemfive.core.client`, part of the `1m5-core` jar (no separate
module). See [`DESIGN.md`](DESIGN.md) §"The embedding contract" and
`1m5-docs/architecture/README.md` §"The 1M5 Core contract" (the `Msg` / `CoreClient`
shape must stay identical across those and `1m5-android/DESIGN.md`).

- [ ] `CoreClient` interface (~8 verbs), `Msg` (flat: `id`, `to`, `sender`,
      `Map<String,String> headers`, `byte[] payload`, `Deque<String> slip`,
      `int attempts`; routing scalars in reserved `x.*` headers), `ProtocolHandle`,
      `CoreInbound`, `ReplyHandler`, `TransportStatus`, `IdentityStatus`.
- [ ] `ProtocolService.channelName()`; `RoutingService` routes on it, not
      `getClass().getName()`; `Core` `name → service` alias table.
- [ ] `EmbeddedCoreClient`: `Msg` ↔ `ra.common.Envelope` translation over
      `Core.get()`; `x.*` headers ↔ `Envelope` scalar setters; slip reversed
      front-to-back ↔ LIFO stack; `HandleBackedProtocolService` wrapping a
      `ProtocolHandle`.
- [ ] `CoreClientContractTest` (abstract) — runs against `EmbeddedCoreClient` now,
      `HttpCoreClient` when the ADR-0003 RPC API lands.
- [ ] JSON golden files for the `Msg` wire form (`did-vectors`-style fixtures).
      This JSON is also the ADR-0003 RPC envelope encoding.

---

## P3 — Default protocol services & the desktop RPC API

- [x] `NetworkServiceProtocol` — adapter bridging any `ra.common.network.NetworkService`
      to `ProtocolService` (lifecycle + status + `sendOut`).
- [~] `I2PProtocolService` — `NetworkServiceProtocol` around `ra.i2p.I2PService`
      (`i2p-java` 1.7.1). Wired + `Daemon` registers it behind `1m5.i2p.enabled=true`;
      adapter/discovery/routing path tested with a mock `NetworkService`. Not yet
      run against a live I2P network.
- [ ] Router (Stage 1b): set real `SimpleExternalRoute` destination `NetworkPeer`s
      (I2P base64 address) so `I2PService.sendOut` has a destination.
- [~] `TorProtocolService` — `NetworkServiceProtocol` around `ra.tor.TORClientService`
      (`tor-client-java` 1.2.1). Wired + `Daemon` registers it behind
      `1m5.tor.enabled=true`. Not run against a live Tor daemon. Tor is local-only.
- [ ] `HTTPProtocolService` — `resolvingarchitecture:http-client`.
- [ ] The **localhost RPC API for `1m5-desktop-java`** (ADR-0003): a handler that
      speaks the `Msg` JSON encoding (unary calls + an inbound stream), bound to
      `127.0.0.1` with a token; `HttpCoreClient` in the `CoreClient` package is
      the desktop-side implementation; `CoreClientContractTest` runs against it.
      Dependency-light JSON/HTTP + WebSocket — no protobuf/gRPC (one consumer,
      same language). `1m5-core-rust` does **not** need this — `1m505` embeds the
      core in-process.
- [ ] `BluetoothProtocolService` — `resolvingarchitecture:bluetooth-client`.
- [ ] `NotificationService` registered (status/event pub-sub; `BaseService.updateStatus`
      routes there).
- [ ] `Transport` capability/estimate methods (latency, reliability, battery, privacy
      properties) so the router can rank, not just pick.
- [ ] Later transports: WiFi-Direct, Satellite, Full-Spectrum Radio, LiFi.

---

## Deferred — separate efforts, not this repo (tracked for visibility)

- [ ] **Desktop**: point `1m5-desktop-java`'s pom at `network.onemfive:1m5-core`
      (or `1m5-common`) for the `CoreClient` interface + `Msg`; swap its bespoke
      `DesktopClient` HTTP calls for `HttpCoreClient` against the localhost RPC
      API (ADR-0003).
- [ ] **Android host**: authoritative plan in `1m5-android/DESIGN.md` §"1M5 Core
      Integration" and `1m5-android/TODO.md` §"1M5 Core Integration". `:core-host`
      module; `I2PProtocolAdapter` / `TorProtocolAdapter` wrapping Remnant's
      existing embedded transports; `AndroidPassphraseProvider`; `OneMFiveApplication`
      compat shim; service-by-service cutover. No code in either repo this pass.
- [ ] **`1m5-core-rust`**: the Rust implementation of this design, embedded by
      `1m505` (Redox OS). Kept in step with this repo (shared `Msg` wire form,
      mirrored escalation engine). Not a candidate for the Android app — Remnant
      embeds the in-process Java core.

## `1m5-common` — keeps `1m5-desktop-java` on Java 11

- [ ] Create `network.onemfive:1m5-common` (Java 11): `ManCon`, `ManConStatus`,
      `ManConStatusListener`, and any other model shared between core, desktop, and
      a future Android host.
- [ ] `1m5-core` depends on `1m5-common`; re-export nothing desktop-specific.
- [ ] (separate effort, deferred) point `1m5-desktop-java` at `1m5-common` +
      `ra-common` for the models it compiles against; it reaches a running core
      through `HttpCoreClient` (ADR-0003), not by embedding this jar.

## Dependency distribution — build-locally-and-pull-in

- [ ] `tools/build-ra-libs.sh` — build the sibling repos in dependency order
      (`ra-common-java` → `seda-bus-java` → `service-bus-java` → `did-java` →
      `i2p-java` / `tor-client-java` → `1m5-core-java`), `mvn install` each.
- [ ] `DEPENDENCIES.md` — the order, the versions, and the `mavenLocal()` wiring
      consumers need. Referenced by `1m5-android/TODO.md` Stage 0.

## External library follow-up — not this repo

- [x] `seda-bus-java` → `1.3.1` (compile target Java 17 → 11).
- [x] `service-bus-java` → `1.5.0`: Class-based API + discovery + `awaitRunning` +
      pause/restart + per-service status + reusable `Daemon`.
- [x] **`ra-common-java 1.3.2`** — released, `mvn install`ed, pinned here:
  - [x] `BaseRoute.fromMap` `"routedId"` → `"routeId"` (routeId now survives
        slip JSON round-trip);
  - [x] `TextMessage.toMap` / `fromMap` serialize `to` / `from` / `text`;
  - [x] `RouteRoundTripTest` added (3 cases); full ra-common suite 6 green.

## Later

- [ ] `1m5-transports` module per `1m5-docs/architecture/README.md`.
- [ ] Adaptive SEDA controller (`seda-bus` 2.0): runtime per-stage thread re-tuning,
      automatic load shedding.
- [ ] User-selectable routing modes (max privacy / max reliability / lowest battery /
      local-only / internet-only / manual / emergency fallback).
- [ ] Routing diagnostics: expose the `SituationalAwareness` trail per envelope.
