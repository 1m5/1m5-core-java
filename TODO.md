# 1M5 Core — TODO

Phased roadmap. Everything here is scoped to `1m5-core-java` unless a section says
otherwise. See [`DESIGN.md`](DESIGN.md) for the architecture each item builds toward.

---

## P0 — Skeleton (done)

- [x] Rename to `network.onemfive:1m5-core:0.1.0`, package `network.onemfive.core`,
      Java 17 target.
- [x] `pom.xml` declares one `resolvingarchitecture` dependency - `service-bus:1.5.0`
      - taking `seda-bus:1.3.1` and `common:1.2.0` transitively.
- [x] `service-bus-java` upgraded to carry the generic service-management weight
      (Class-based API, discovery, `awaitRunning`, pause/restart, reusable `Daemon`);
      `seda-bus-java` retargeted to Java 11. `1m5-core` consumes both.
- [x] `Core` = thin runtime handle (bus + ManCon state); `Daemon` extends
      `ra.servicebus.Daemon` and only fills the 1M5 hooks.
- [x] Service taxonomy: `CoreService` / `BusinessService` / `DataService` /
      `ProtocolService` + `Transport`, `ServiceType`. Protocol adapters discovered
      via `serviceBus.findRunningServices(ProtocolService.class)`.
- [x] ManCon model ported: `ManCon`, `ManConStatus` (instance, not statics),
      `ManConStatusListener`, `SituationalAwareness`.
- [x] `RoutingService` skeleton — picks the first ready `ProtocolService`, pushes one
      `SEND` hop, applies ManCon delay/copy parameters.
- [x] `IdentityService` skeleton — establishes a stable node key (secp256k1 via JCA
      when available, else placeholder).
- [x] `PeerDirectory` skeleton.
- [x] `CoreSmokeTest` (bus + slip, single and multi-hop) and `RoutingServiceTest`
      (router selects a connected protocol) — green.
- [x] Rewrite `README.md`, `DESIGN.md`; drop stale `RELEASE-NOTES.md`, `BUILD.md`,
      `ops/`.

---

## P1 — Router parity with `CRNetworkManagerService`

Port the escalation logic from `onemfive.routing.CRNetworkManagerService`:

- [ ] ManCon x (web request | P2P) decision matrix.
- [ ] Network selection by ManCon (LOW/MEDIUM -> Tor/I2P by address; HIGH -> I2P->Tor;
      VERYHIGH -> I2P + random delays; EXTREME/NEO -> non-internet relay).
- [ ] Relay-peer selection: "blocked on Tor -> use I2P to reach a peer that isn't",
      and the Bluetooth/WiFi/Satellite/FSRadio/LiFi escalation ladder.
- [ ] `RelayedExternalRoute` hops (origination/destination peers, per-hop delay and
      copy parameters).
- [ ] Message hold + retry when no path exists (replace the current error-and-drop).
- [ ] `ManConStatus.maxAvailable` probing from timestamped per-level connectivity
      tests; fire `ManConStatusListener`s on change.
- [ ] Real peer store: add `resolvingarchitecture:network-manager`, back
      `PeerDirectory` with `ra.networkmanager.InMemoryPeerDB` / `P2PRelationship`
      (ack latency, reliability scoring, relationship graph).
- [ ] Router unit tests per ManCon level and per blocked-network scenario.

---

## P2 — Identity (ADR-0002)

- [ ] Bundle a real secp256k1 implementation (evaluate a small vendored primitive vs.
      `bcprov`; Android already ships BouncyCastle).
- [ ] x-only public key derivation; hex + optional `npub` (Bech32).
- [ ] BIP-340 Schnorr sign/verify; SHA-256 event ids; canonical event serialization,
      with test vectors (valid + malformed).
- [ ] Node identity + user identities; key-domain separation (messaging / encryption
      / wallet / transport / device / session).
- [ ] Private keys encrypted at rest with a `1m5.pass`-derived key; never logged.
- [ ] Legacy `DIDService` (read-only) via `resolvingarchitecture:did` for verifying
      old contacts.
- [ ] OpenPGP -> Nostr migration proof: create + verify, binding legacy fingerprint,
      new pubkey, app context, timestamp, user intent.
- [ ] Contact model + trust states (`unverified`, `verified_by_qr`,
      `verified_by_openpgp_migration`, `verified_by_existing_conversation`,
      `rotated`, `blocked`, `compromised`); key rotation records.
- [ ] E2EE envelope: secp256k1 ECDH + HKDF-SHA256 + an AEAD (ChaCha20-Poly1305 /
      AES-GCM); session keys; replay protection.
- [ ] Threat-model the identity + encryption design before calling it stable; invite
      cryptographic review.

---

## P3 — Default protocol services

- [x] `NetworkServiceProtocol` — adapter bridging any `ra.common.network.NetworkService`
      to `ProtocolService` (lifecycle + status + `sendOut`).
- [~] `I2PProtocolService` — `NetworkServiceProtocol` around `ra.i2p.I2PService`
      (`i2p-java` 1.7.1). Wired + `Daemon` registers it behind `1m5.i2p.enabled=true`;
      the adapter/discovery/routing path is tested with a mock `NetworkService`. Not
      yet run against a live I2P network (embedded reseed; `ra.i2p.mode=local` needs
      field testing - see i2p-java's TODO).
- [ ] Router (P1): set real `SimpleExternalRoute` destination `NetworkPeer`s (I2P
      base64 address) so `I2PService.sendOut` has a destination.
- [ ] `TorProtocolService` — `NetworkServiceProtocol` around
      `resolvingarchitecture:tor-client` (or `tor-java`).
- [ ] `HTTPProtocolService` — `resolvingarchitecture:http-client`; also hosts the
      localhost Envelope-JSON API on `127.0.0.1:2018`
      (`ra.http.EnvelopeJSONDataHandler`) so `1m5-desktop-java` keeps working.
- [ ] `BluetoothProtocolService` — `resolvingarchitecture:bluetooth-client`.
- [ ] `NotificationService` registered (status/event pub-sub; `BaseService.updateStatus`
      routes there).
- [ ] `Transport` capability/estimate methods (latency, reliability, battery, privacy
      properties) so the router can rank, not just pick.
- [ ] Later transports: WiFi-Direct, Satellite, Full-Spectrum Radio, LiFi.

---

## Deferred — separate efforts, not this repo (tracked for visibility)

- [ ] **Desktop**: point `1m5-desktop-java`'s pom at
      `network.onemfive:1m5-core`; verify `DesktopClient`'s `ControlCommand` /
      `addRoute` envelope shapes against the new API handler; optionally an
      embed-`Core` path instead of HTTP.
- [ ] **Android host**: `Core` inside a foreground `Service`; Android
      `ProtocolService` adapters over the embedded I2P router and `tor-android`;
      `Intent` <-> `Envelope` boundary; retire duplicated
      `network.onemfive.android` router / identity / json code.

## `1m5-common` — keeps `1m5-desktop-java` on Java 11

`1m5-desktop-java` compiles against this jar today only for `ManCon` /
`ManConStatus` / `ManConStatusListener` (everything else it uses is transitive
`ra.*`), and it talks to the daemon over HTTP rather than embedding it.

- [ ] Create `network.onemfive:1m5-common` (Java 11): `ManCon`, `ManConStatus`,
      `ManConStatusListener`, and any other model shared between core, desktop, and
      a future Android host.
- [ ] `1m5-core` depends on `1m5-common`; re-export nothing desktop-specific.
- [ ] (separate effort, deferred) point `1m5-desktop-java` at `1m5-common` +
      `ra-common` instead of the full core jar.

## External library follow-up — not this repo

- [x] `seda-bus-java` -> `1.3.1` (compile target Java 17 -> 11).
- [x] `service-bus-java` -> `1.5.0`: `seda-bus` pin bumped to `1.3.1`; Class-based
      API + discovery + `awaitRunning` + pause/restart + per-service status +
      reusable `Daemon`; fixed `Properties.contains`, the `gracefulShutdown` wait
      loop, and `PersistDeadLetter` rotation. (See that repo's `TODO.md` for its
      remaining backlog: readiness gate, health policy, `ControlCommand` responses.)
- [ ] `ra-common-java`: fix `BaseRoute.fromMap` `"routedId"` typo;
      `TextMessage.toMap` / `fromMap` do not serialize their fields.

## Later
- [ ] `1m5-transports` module per `1m5-docs/ARCHITECTURE.md`.
- [ ] Adaptive SEDA controller (`seda-bus` 2.0): runtime per-stage thread re-tuning,
      automatic load shedding.
- [ ] User-selectable routing modes (max privacy / max reliability / lowest battery /
      local-only / internet-only / manual / emergency fallback).
- [ ] Routing diagnostics: expose the `SituationalAwareness` trail per envelope.
