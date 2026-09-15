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
- [x] **Jurisdiction default for `minRequired`**: `jurisdictions-levels.txt`
      bundled as a resource; `ManConStatus.defaultFor(String iso2)` +
      `applyJurisdiction(iso2)`; the `Daemon` seeds `minRequired` from the
      `1m5.jurisdiction` config key / env var. `ManConStatusTest` covers
      NO→LOW / GB→MEDIUM / US→HIGH / ZW→VERYHIGH / CN→EXTREME / unknown→HIGH.
      User→jurisdiction lookup stays the host's job.

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
shape must stay identical across those and `1m5-remnant/DESIGN.md` -
`1m5-android/DESIGN.md`'s own copy is superseded; see that repo's doc-pointer
follow-up in `1m5-remnant/DESIGN.md` §"Impact on 1m5-android").

- [x] `CoreClient` interface (10 verbs), `Msg` (flat: `id`, `sender`,
      `Map<String,String> headers`, `byte[] payload`, `Deque<Hop> slip`,
      `int attempts`; routing scalars in reserved `x.*` headers), `Msg.Hop`
      (`channel` + `operation`), `ProtocolHandle`, `CoreInbound`, `ReplyHandler`,
      `TransportStatus`, `IdentityStatus`. A `Hop`'s `channel`/`operation` mirror
      `Route.getService()`/`Route.getOperation()` exactly - a channel is never
      addressed without saying which method to invoke on it, so the two are one
      value, not two independent `Msg` fields (a protocol channel only ever
      implements one operation, `OPERATION_SEND`, applied regardless of what a
      `Hop` says; a business/data channel requires the caller to set it - see
      `EmbeddedCoreClientTest.sendWithOperationInvokesTheNamedMethodOnABusinessChannel`).
      `Msg.to(channel, operation)` is the common single-hop convenience.
- [x] `ProtocolService.channelName()` (defaults to `getNetwork().name()`);
      `RoutingService` routes on it via `Core.resolveChannel(String)`, not
      `getClass().getName()`; `Core.resolveChannel` is the `name → service` alias
      table, resolved lazily against `ServiceBus.getRegisteredServiceNames()`
      (no `service-bus` change, as planned).
- [x] `EmbeddedCoreClient`: `Msg` ↔ `ra.common.Envelope` translation
      (`MsgTranslator`) over `Core.get()`; `x.*` headers ↔ `Envelope` scalar
      setters; slip reversed front-to-back ↔ LIFO stack; `HandleBackedProtocolService`
      wrapping a `ProtocolHandle`, registered under the handle's own `name()` via a
      lock-guarded static handoff (construction is synchronous inside
      `ServiceBus.registerService`, so this needs no `service-bus` change either).
      `IdentityService.signAsNode(byte[])` added (BIP-340 sign over SHA-256 of the
      caller's bytes via `did-java`'s `Bip340.sign`, no `did-java` change) since
      `signAsNode(NostrEvent)` alone couldn't back the `CoreClient` verb.
      `EmbeddedCoreClientTest` covers identity, signing, protocol registration, and
      a full send round-trip through a fake `ProtocolHandle` — green.
- [x] **Resolved - inbound delivery now has somewhere to go.**
      `registerChannel(String channel, CoreInbound handler)` (the 10th verb) +
      `AppChannelService` (the `HandleBackedProtocolService` counterpart for a
      business channel the app owns, e.g. `"messaging"`): registered on the bus
      under `channel` via the same lock-guarded static handoff; `handleDocument`
      converts the arriving `Envelope` back to a `Msg` and calls
      `handler.accept(msg)`. `CoreInbound` is reused as the callback shape in
      both directions rather than adding a near-identical second type.
      **Real finding, fixed same-day**: `AppChannelService` needs to be `public`
      (not package-private) - `ServiceBus`'s reflective construction throws
      `IllegalAccessException` otherwise; caught immediately by the new tests
      below, exactly why they were worth writing rather than trusting the design
      by inspection.
      `EmbeddedCoreClientTest.registeredChannelReceivesAMessageAddressedToIt` and
      `.aProtocolHandlesInboundMessageReachesARegisteredChannel` (the latter
      simulates exactly what a real `ProtocolHandle` does on inbound - construct
      a `Msg` addressed at the app's channel and call the `CoreInbound`
      `registerProtocol` returned) - both green, 39/39 total.
      **Still open, not this change**: this only proves the in-process path.
      Wiring a real transport end-to-end in `1m5-remnant` additionally needs the
      transport adapters to actually address inbound `Msg`s at a business
      channel (today they build a bare sender+payload `Msg` with an empty slip -
      see `1m5-remnant/TODO.md`), and, for a genuinely cross-node business
      channel (not a single hardcoded `"messaging"` convention), the `Msg` wire
      JSON below so a remote peer's own routing intent survives the wire.
- [x] `Msg.Hop` (channel + operation, replacing separate `to`/`operation` fields
      and a bare-channel-name `slip`) landed in `DESIGN.md`,
      `1m5-docs/architecture/README.md`, and `1m5-remnant/DESIGN.md` - all three
      read identically per this section's own rule.
- [ ] `x.relay.peerId` / `x.error.*` round-trip as plain headers but do not yet
      drive `RelayedExternalRoute` construction on the outbound path (the router
      builds those itself when it decides to relay) - revisit if an app-originated
      relay hint becomes a real use case.
- [x] **`PeerDirectory` is now reachable from `CoreClient`.** Found while
      designing `1m5-remnant`'s messaging outbound path: an app can never touch
      `PeerDirectory`/`NetworkPeer` directly (bus-internal types), and nothing
      populated it from a `CoreClient`-originated `Msg`, so `dispatchDirect`
      could never find an address-matched destination for any contact the app
      didn't already know a raw network address for. Fixed with
      `RoutingService.OPERATION_REGISTER_PEER`: a `Msg` addressed at
      `RoutingService.class.getName()` with that operation, carrying
      `x.dest.peerId` (fingerprint) and any of `x.dest.i2p`/`x.dest.tor`/
      `x.dest.bt` it has learned, registers one `NetworkPeer` per address header
      present. No new `CoreClient` verb (ADR2 in `1m5-remnant/DESIGN.md`
      deliberately keeps app-specific verbs out) - this reuses the existing
      "business channel + operation" mechanism, symmetric with
      `IdentityService.OPERATION_GET_NODE_IDENTITY`.
      **Real finding, fixed alongside it**: `MsgTranslator.toEnvelope` computed
      the `x.dest.*` values from `Msg` headers but only ever used them for a
      *protocol*-channel hop's `ExternalRoute` - addressing `RoutingService`
      (a business channel) silently dropped them all, so
      `RoutingService.facts()`'s own header fallback for the destination
      fingerprint could never fire from any `CoreClient`-originated `Msg`. Fixed
      by also setting them as plain envelope headers unconditionally.
      `EmbeddedCoreClientTest.sendToRoutingServiceWithOnlyAFingerprintReachesTheAddressMatchedProtocol`
      proves the full path: register a peer's I2P address, then send with only
      a fingerprint and confirm it reaches the right `ProtocolHandle` with the
      right `x.dest.i2p` header attached. 40/40 green.
      **Still open, not this change**: no liveness/reliability scoring
      (`PeerDirectory`'s own documented skeleton-scope limit, unrelated to this
      fix) and no contact/address-exchange format to actually *learn* a
      contact's addresses in the first place - that is `1m5-remnant`'s to
      design (its `TODO.md` §"Step 4" tracks it), not this module's.
- [x] **`RoutingChannel`** (`network.onemfive.core.client`): the channel name,
      both operation names, and the destination header keys for addressing
      `RoutingService`, as plain `String` constants in the `CoreClient`
      package - found needed immediately while wiring `1m5-remnant`'s outbound
      send, since without it a host has no way to build a `Msg` targeting
      `RoutingService` without importing the bus-internal
      `network.onemfive.core.routing` package outright, which ADR 2 in
      `1m5-remnant/DESIGN.md` rules out. `RoutingChannelTest` asserts the
      constants stay identical to `RoutingService`'s own (duplicated
      deliberately, not shared - sharing would require the one import this
      avoids). 41/41 green.
- [x] **`ProtocolHandle.localAddress()`** / **`TransportStatus.getLocalAddress()`**:
      found needed for `1m5-remnant`'s "My Card" screen, which has to publish
      this device's own I2P/Tor/BT address for a contact to import - and
      nothing surfaced it. `ProtocolHandle.localAddress()` (default null, so no
      existing implementation breaks) is the host's own address on that
      transport; `ProtocolService.getLocalAddress()` (also default null)
      carries it through, `HandleBackedProtocolService` overrides it to
      delegate to the handle, and `TransportStatus` gained a fourth
      constructor param + getter (old 3-arg constructor kept, delegating with
      null, so every existing call site is untouched).
      `EmbeddedCoreClientTest.readyTransportsSurfacesTheHostsOwnLocalAddress`
      proves it round-trips through `registerProtocol` → `readyTransports()`.
      42/42 green.
- [ ] `CoreClientContractTest` (abstract) — runs against `EmbeddedCoreClient` now,
      `HttpCoreClient` when the ADR-0003 RPC API lands. Not built yet;
      `EmbeddedCoreClientTest` is a concrete, non-abstract stand-in until there is a
      second `CoreClient` implementation to share it with.
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
- [~] `HttpProtocolService` — `NetworkServiceProtocol` around `ra.http.HTTPService`
      (`resolvingarchitecture:http-client` 1.2.0). Wired + `Daemon` registers it
      behind `1m5.http.enabled=true`. Clearnet, no anonymity; not run against a
      live network.
- [x] `network.onemfive.core.business.BitcoinService` — composes `ra.btc.BitcoinService`
      (`resolvingarchitecture:btc` 2.0.2, ported to bitcoinj 0.17.1) as a
      `BusinessService`. Wired + `Daemon` registers it behind `1m5.bitcoin.enabled=true`.
      Peer discovery/connections still don't route through the router's transports
      (`getPeers` deliberately returns nothing; `ra.btc.socks.host`/`ra.btc.socks.port`
      let bitcoinj's own peer group connect through a local SOCKS proxy in the
      meantime) — real routing through I2P/Tor is still a TODO in `bitcoin-client-java`.
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
