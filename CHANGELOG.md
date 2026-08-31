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
- `CoreSmokeTest`, `RoutingServiceTest` — green.
- Removed the legacy `onemfive` package, `RELEASE-NOTES.md`, `BUILD.md`, `ops/`.

See `TODO.md` for what is deliberately not yet implemented.
