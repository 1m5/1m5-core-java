# 1M5 Core Changelog

## 0.1.0 — In progress

Rewrite of the former `onemfive:platform` scaffold into the reusable 1M5 core.

- Renamed to `network.onemfive:1m5-core`, package `network.onemfive.core`, Java 17.
- Built on the bus stack via one declared dependency, `service-bus:1.5.0`, which
  brings `seda-bus:1.3.1` and `common:1.2.0` transitively
  (`1m5-core -> service-bus -> seda-bus -> common`).
- `service-bus-java` was upgraded (1.5.0) to own the generic service-management
  weight - Class-based registration, service discovery
  (`findRunningServices(Class)`), `awaitRunning`, pause/restart, per-service status,
  and a reusable `Daemon` base - so `1m5-core` keeps only its router / ManCon /
  identity concerns. `seda-bus-java` was retargeted to Java 11 (1.3.1).
- Service taxonomy: `BusinessService` / `DataService` / `ProtocolService` (+
  `Transport`), all extending `ra.common.service.BaseService`.
- `RoutingService` — the 1M5 intelligent router, as a bus service that orchestrates
  transport selection by pushing hops onto the `DynamicRoutingSlip`. Skeleton
  selection only; full escalation logic staged (see `TODO.md`).
- `IdentityService` — node identity, targeting ADR-0002 (Nostr / secp256k1);
  skeleton key establishment only.
- `ManCon` model ported (`ManConStatus` now an instance, not global statics).
- `Core` = thin runtime handle (active bus + ManCon state + protocol-readiness
  view); `Daemon extends ra.servicebus.Daemon`, filling only the 1M5 hooks.
- `NetworkServiceProtocol` adapter bridging any `ra.common.network.NetworkService`
  to `ProtocolService`.
  - `I2PProtocolService` wraps `i2p-java` 1.7.1's `ra.i2p.I2PService`
    (`1m5.i2p.enabled=true`). `i2p-java` gained `ra.i2p.mode` (embedded | local |
    auto) + `LocalRouterDetector`, referencing `1m5-android`'s embedded/local split.
  - `TorProtocolService` wraps `tor-client-java` 1.2.1's `ra.tor.TORClientService`
    (`1m5.tor.enabled=true`; local Tor daemon only). `tor-client-java` gained
    `LocalTorDetector` + fail-fast startup.
- `CoreSmokeTest`, `RoutingServiceTest`, `ProtocolIntegrationTest` — green.
- Removed the legacy `onemfive` package, `RELEASE-NOTES.md`, `BUILD.md`, `ops/`.

### Escalation router (P1)

- `RoutingService` rewritten around a pure decision engine, `EscalationRouter`:
  ManCon &times; (web | P2P) network selection (`ManConNetworks`), address-matched
  transport choice, cross-transport relay (`RelayedExternalRoute`), and hold +
  backoff retry (`RetryStrategy`, ported from Remnant's `RouterService`; the
  `RetryScheduler` seam keeps it unit-testable) with dead-letter once the retry
  window is exhausted.
- `ManConStatus.select()` fixed: `maxAvailable` is now driven from ready
  transports (`RoutingService.refreshAvailability()` &rarr; `ManConNetworks
  .maxAvailableFor`), firing `ManConStatusListener`s on change. It previously
  stayed at `NONE` and every `select()` collapsed to `NONE`. Added
  `meetsFloor(...)` so the router holds rather than downgrades when the connected
  transports cannot meet the operator's floor.
- `PeerDirectory` enriched: `fingerprint -> network -> address` plus relay-capable
  peers, for address-matched selection and relay lookup. (The
  `ra.networkmanager` peer DB with reliability scoring is still the follow-up.)
- `SituationalAwareness` carries the full decision trail (band, acceptable
  networks, ready networks, decision, chosen/relay network, attempt, reason) and
  is logged for every envelope.
- Depends on `ra-common 1.3.2` (`BaseRoute.fromMap` `routedId` typo fixed, so
  `routeId` survives slip JSON round-trip).
- Tests: `EscalationRouterTest` (12), `ManConStatusTest` (5),
  `RoutingServiceEscalationTest` (4) - 28 green total.

Still not implemented: the ManCon-driven random-delay ratchet beyond the
VERYHIGH/EXTREME/NEO parameter bands, NEO mnemonic-only keys, live-network relay
testing, and the `ra.networkmanager` peer store.

See `TODO.md` for the rest.
