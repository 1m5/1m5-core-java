# 1M5 Core — Design

## Context

1M5 began as a desktop application exploring one idea: communications and Bitcoin
activity should route intelligently across whatever network paths remain available,
instead of depending on a single channel. That routing model is the project's
strategic asset. It has been re-implemented three times with no shared code — a thin
JVM scaffold (`onemfive:platform`), a complete Android-native version in
`1m5-android`, and an abandoned Rust attempt.

`1m5-core-java` is the consolidation: one reusable, transport-agnostic core built on
the `resolvingarchitecture` bus libraries, able to run headless for
`1m5-desktop-java` today and to be hosted inside `1m5-android` later.

This document describes the target design. Some of it is implemented (the bus stack,
the service taxonomy, the router seam, the embedding facade); the rest is staged in
[`TODO.md`](TODO.md) and called out below as such.

## Goals and constraints

- **Censorship resistance is a design constraint, not a feature.** Assume paths
  disappear, degrade, or become unsafe. Favour fallback, decentralization, and local
  control over convenience.
- **The router is transport-agnostic and reusable.** It must not be coupled to any
  UI or to any one transport implementation.
- **Pure JVM.** No JavaFX, no `android.*`, no desktop-only dependencies — so the same
  core can be embedded by a headless daemon and by an Android foreground service.
- **Honest about limitations.** Censorship resistance is not anonymity; encryption
  does not hide metadata; transport diversity reduces but does not eliminate risk.

## Coordinates

| | |
|---------------|----------------------------------|
| groupId       | `network.onemfive`               |
| artifactId    | `1m5-core`                        |
| version       | `0.1.0`                          |
| base package  | `network.onemfive.core`          |
| bytecode      | Java 17 (`maven.compiler.release=17`) |
| build/run JDK | 17+                              |

Renamed from `onemfive:platform:2.6.21`. Version reset to `0.1.0` — this is a
rewrite, not a continuation.

---

## The layer stack

`1m5-core` declares **one** `resolvingarchitecture` dependency - `service-bus` - and
takes the rest of the chain transitively, at the versions `service-bus-java` was
built and tested against:

    1m5-core  ->  service-bus  ->  seda-bus  ->  common

    ra-common        resolvingarchitecture:common:1.2.0        (transitive; Java 8)
      Envelope                 wraps every payload; carries the routing slip
      DynamicRoutingSlip       the itinerary - a LIFO stack of Route
      Route / SimpleRoute / SimpleExternalRoute / RelayedExternalRoute
      BaseService              abstract service: producer wiring + handle* dispatch
      MessageBus / MessageChannel / MessageConsumer / MessageProducer
      ServiceLevel             AtMostOnce | AtLeastOnce | ExactlyOnce
      identity.DID             legacy OpenPGP identity model
        ^
    seda-bus         resolvingarchitecture:seda-bus:1.3.1      (transitive; Java 11)
      SEDABus                  MessageBus impl; channel registry; publish
      SEDAMessageChannel       one stage: bounded ArrayBlockingQueue + consumers
      WorkerThreadPool         event-driven pool, per-stage concurrency permits
      -> delivery levels, retry + dead-letter, atomic-write durable replay
      -> THE ROUTING-SLIP EXECUTION ENGINE (SEDABus.completed, below)
        ^
    service-bus      resolvingarchitecture:service-bus:1.5.0   (direct dep; Java 11)
      ServiceBus              reflective registry; one channel per service (keyed
                             by class name); dependency-ordered registration;
                             Class-based register/start; service discovery
                             (getService / findRunningServices); awaitRunning;
                             pause/unpause/restart; per-service status + listeners;
                             ControlCommand envelopes; rotating dead-letter file
      Daemon                 reusable headless host (configName / beforeStart /
                             onBusStarted / onStopping hooks)
        ^
    1m5-core         network.onemfive:1m5-core:0.1.0           (this module; Java 17)
      service taxonomy, RoutingService, ManCon model, IdentityService,
      Daemon (extends ra.servicebus.Daemon) + Core state/discovery handle

`service-bus:1.5.0` pins `seda-bus:1.3.1` (event-driven worker pool - removing a
~10 msg/s/channel ceiling - working pub/sub and `pause`, retry + dead-letter,
hardened persistence). `1m5-core` gets all of that transitively, with no
`<dependencyManagement>` override.

### Java version

`1m5-core` targets **Java 17** - a modern baseline, matching the `1m5-android` app
module. The bus chain sits below it at Java 11 (`service-bus`, `seda-bus`) and Java 8
(`common`); a Java 17 build consumes them without issue.

`1m5-desktop-java` stays on Java 11 without a forced upgrade: it depends on the core
jar today only for `ManCon` / `ManConStatus` / `ManConStatusListener` (plus transitive
`ra.*` jars) and it talks to the daemon over HTTP, not by embedding it. Extracting
those few classes into a small Java-11 `1m5-common` (see [`TODO.md`](TODO.md)) lets
desktop drop its dependency on this jar entirely. A later desktop upgrade to 17 is
possible but gated by its own UI stack (jfoenix), not by this module.

### Why SEDA

Staged Event-Driven Architecture (Welsh, Culler, Brewer — SOSP 2001): work is
decomposed into stages connected by bounded queues, drained by one shared worker
pool, each stage capped at its own concurrency so none can starve the others. The
bounded queues are admission control — back-pressure surfaces to the producer as a
failed `send`. This fits a censorship-resistant router: transports have wildly
different latency and throughput, and the router must degrade gracefully under load
rather than collapse.

The adaptive controller from the original SEDA paper (runtime re-tuning of per-stage
threads from measured latency, automatic load shedding) is **not** implemented in
`seda-bus` — every setting is static config. That is `seda-bus` 2.0 material.

---

## How an envelope moves

The bus stack already implements this. `1m5-core` does not reimplement it; it uses
it.

1. A producer builds an `Envelope`, adds hops with
   `envelope.addRoute(ServiceClass, "OPERATION")` or `addExternalRoute(...)` — each
   call **pushes** a `Route` onto the `DynamicRoutingSlip` (a LIFO stack) — then
   `envelope.ratchet()` once to load the first route, then `core.send(envelope)`.
2. `ServiceBus.send` -> `SEDABus.publish` -> `lookupChannel` resolves the target
   channel from `route.getService()` -> `channel.send` (enqueue) + `pool.schedule`.
3. A worker calls `channel.poll` -> `process` -> `consumer.receive(envelope)`. The
   consumer is the target `BaseService`; `receive` dispatches by message type to
   `handleDocument` / `handleEvent` / `handleCommand` / `handleHeaders`, then
   `reply` sets `route.setRouted(true)`.
4. On success the channel calls **`SEDABus.completed(envelope)`**:
   - if `slip.peekAtNextRoute() != null` -> `envelope.ratchet()` (pop the next
     route) -> schedule its channel;
   - else remove and fire the producer's `Client` callback.

So a service orchestrates simply by pushing a route and returning — the engine
carries the envelope onward.

### The LIFO trap, and the router pattern

`DynamicRoutingSlip` is a stack: `addRoute` pushes, `nextRoute` pops. Pushing a full
multi-hop itinerary in one method executes it in **reverse**. Two safe patterns:

- add hops in reverse execution order (what the tests do for a fixed itinerary);
- **decide one hop at a time** — the router pushes exactly one route, returns, lets
  SEDA carry the envelope, and re-inspects if more routing is needed. This is the
  pattern `RoutingService` uses and the one to prefer.

Keeping the LIFO semantics preserves compatibility with `ra-common` and every other
consumer of the slip. A FIFO variant is possible but out of scope.

---

## Service taxonomy

`network.onemfive.core.service`:

    CoreService (abstract)                extends ra.common.service.BaseService
      + ServiceType getServiceType()
        |
        +-- BusinessService (abstract)    ServiceType.BUSINESS
        +-- DataService     (abstract)    ServiceType.DATA
        +-- ProtocolService (abstract)    ServiceType.PROTOCOL, implements Transport

- **`BusinessService`** — internal domain logic and orchestration. Coordinates other
  services via the routing slip; owns no durable state. The router is one.
- **`DataService`** — owns durable local state. Should register its channel with
  `ServiceLevel.AtLeastOnce`/`ExactlyOnce` so writes survive a crash and replay.
- **`ProtocolService`** — an external transport adapter. Implements `Transport`:

      Network      getNetwork()
      NetworkStatus getNetworkStatus()
      boolean      isReady()            // CONNECTED or VERIFIED
      boolean      send(Envelope)       // carry it out over this network

  The router discovers protocol adapters through the bus -
  `serviceBus.findRunningServices(ProtocolService.class)` - and reads their
  `getNetworkStatus()` directly; no parallel registry. The standard inbound
  operation is `SEND`.

Concrete `ProtocolService` implementations are **per platform**:

- default JVM daemon: wrap the `resolvingarchitecture` network-client jars via
  `network.onemfive.core.protocol.NetworkServiceProtocol` (below); `ra.tor`,
  `ra.bluetooth` staged, [`TODO.md`](TODO.md);
- Android host: adapters over the embedded I2P router (`net.i2p:router`) and
  `tor-android`, implementing this same class and registering on the same bus.

### `NetworkServiceProtocol` — bridging `ra.common.network.NetworkService`

`i2p-java`, `tor-client`, and `bluetooth-client` all extend
`ra.common.network.NetworkService` (the RA base: a `NetworkState` with network +
`NetworkStatus` + local/remote peers, `updateNetworkStatus`, `OPERATION_SEND`).
That is *not* a 1M5 `ProtocolService`, so the router would not discover it.

`NetworkServiceProtocol` (abstract, `extends ProtocolService`) adapts one:

- it is the bus-registered service; it *owns* the wrapped `NetworkService` (not
  bus-registered), hands it the same `MessageProducer` so its inbound envelopes
  flow back onto the bus, and forwards `start` / `shutdown`;
- `getNetwork()` / `getNetworkStatus()` read the wrapped service's `NetworkState`;
- `send(Envelope)` delegates to a subclass `sendOut(...)` (the wrapped service's
  public `sendOut`).

`I2PProtocolService` is `NetworkServiceProtocol` around `ra.i2p.I2PService`
(`i2p-java`), registered when `1m5.i2p.enabled=true` (an embedded I2P router
reseeds on first start; mode via `ra.i2p.mode` = `embedded` | `local` | `auto`).

`TorProtocolService` is `NetworkServiceProtocol` around `ra.tor.TORClientService`
(`tor-client-java`), registered when `1m5.tor.enabled=true`. Tor is local-only - it
needs a Tor daemon already running on the host (SOCKS 9050 / control 9051); the
adapter's `start()` returns false cleanly, with an actionable log line, when none
is found. Bluetooth follows the same shape.

---

## The 1M5 intelligent router — `routing.RoutingService`

A `BusinessService`. Envelopes bound for a peer or a URL are addressed to it first
(`envelope.addRoute(RoutingService.class, RoutingService.OPERATION_ROUTE)`).

For each envelope it builds a `SituationalAwareness` snapshot from:

- `envelope.getURL()` — web request vs. peer-to-peer;
- `envelope.getSensitivity()` (0–10) -> `ManCon.fromSensitivity(...)`;
- the `ManConStatus` band (min required / max available / max supported);
- which `ProtocolService`s report `CONNECTED`, from `Core.readyProtocols()` (a thin
  view over `serviceBus.findRunningServices(ProtocolService.class)`);
- (later) peer reachability from the peer directory.

It then **pushes one transport hop** onto the slip
(`envelope.addExternalRoute(protocolServiceClass, "SEND")`), sets ManCon-derived
delivery parameters (`setDelayed`/`setMinDelay`/`setMaxDelay`,
`setCopy`/`setMinCopies`/`setMaxCopies`), and returns. It does **not** ratchet — the
engine does. If nothing can carry the envelope it records an error (hold/retry is
staged).

Route selection must be **explainable** — the `SituationalAwareness` snapshot exists
so every decision (only route available, lowest latency, best privacy, user
preference, fallback after failure) can be logged.

### Skeleton vs. target

The skeleton chooses the first ready protocol (honouring a protocol already named on
the slip). The **target** ports the full escalation logic from
`onemfive.routing.CRNetworkManagerService`:

- ManCon x (web request | P2P) decision matrix;
- Tor <-> I2P <-> Bluetooth relay selection when the desired network is blocked
  ("if I'm blocked on Tor, use I2P to reach a peer that isn't");
- random delays that ratchet up with ManCon;
- NEO: multiple encrypted copies, delays up to months, mnemonic-only key;
- message hold + retry when no path exists;
- `ManConStatus.maxAvailable` probing from timestamped per-level connectivity tests.

---

## ManCon model

`network.onemfive.core`:

- **`ManCon`** — `NEO(0) EXTREME(1) VERYHIGH(2) HIGH(3) MEDIUM(4) LOW(5) NONE UNKNOWN`.
  Ordinal runs most-severe-first. `fromSensitivity(0..10)` maps an envelope's
  sensitivity to a level; `HIGH` is the 1M5 default for P2P.
- **`ManConStatus`** — an **instance** (owned by `Core`), not global mutable statics
  as in the legacy code. Holds min required / max available / max supported and a
  `select(requested)` that clamps a request into the achievable band.
- **`ManConStatusListener`** (`extends Runnable`) — hosts register to refresh what
  they show the user when the band changes.
- **`SituationalAwareness`** — the per-envelope decision snapshot.

ManCon levels correspond to jurisdictions on the Press Freedom Index; recommended
levels and the full narrative live in `1m5-docs`.

---

## Identity — `identity.IdentityService`

A `DataService` owning the node identity (and later user identities and contacts).

**Direction (1m5-docs ADR-0002):** move from OpenPGP to **Nostr-compatible**
identity — secp256k1 32-byte private key, x-only public key, hex / optional `npub`,
BIP-340 Schnorr signatures, SHA-256 event ids, canonical event serialization, keys
encrypted at rest. Small auditable primitives, **no** Nostr client or relay
libraries. Legacy OpenPGP (`ra.common.identity.DID` + keyring) is retained
**read-only** for verifying legacy contacts and OpenPGP->Nostr migration proofs.

Key domains stay separate: messaging identity, encryption, wallet, transport, device,
session. Bitcoin wallet keys must never automatically become messaging identities.

**Skeleton scope:** establish a stable 32-byte node secret and a public id — JCA
secp256k1 when the JDK provides it, otherwise a `SecureRandom` secret with a
SHA-256-derived placeholder id (some JDK builds omit secp256k1). Proper point/x-only
derivation, BIP-340, the event model, encryption-at-rest, and migration proofs need a
real secp256k1 implementation and are a later phase.

---

## Daemon and the `Core` handle

Service management is `service-bus`'s job: `ServiceBus` does register, start,
discover, `awaitRunning`, pause/restart, status. 1M5 Core adds only what is
1M5-specific.

### `Core` — the runtime handle

A process-wide singleton (`Core.reset()` for tests) holding the active `ServiceBus`
and the `ManConStatus` instance. Two ways in:

    // embedded / tests: Core builds and owns the bus
    Core.get().start(properties);

    // daemon: the Daemon owns the bus, Core is bound to it
    Core.get().bind(serviceBus, config);

Then, for everyone:

    Core.get().registerServices(IdentityService.class, RoutingService.class);
    Core.get().awaitServices(15_000, RoutingService.class);
    Core.get().send(envelope[, replyClient]);
    Core.get().readyProtocols();     // findRunningServices(ProtocolService.class), ready ones
    Core.get().manConStatus();

`registerServices` / `awaitServices` / `send` are thin delegates to `ServiceBus`.
`Core` exists for the ManCon state and the protocol-readiness view - not to
re-wrap the bus.

### `Daemon` — the standalone entry point

`network.onemfive.core.Daemon extends ra.servicebus.Daemon`. The base class owns the
bus lifecycle, the shutdown hook, and the idle loop; the 1M5 subclass fills the
hooks:

- `configName()` -> `"1m5-core.config"`
- `beforeStart(config)` -> logging, time zone, version, `~/.1m5/core/{config,data,
  cache,pid,logs,tmp}`
- `onBusStarted(bus, config)` -> `Core.get().bind(bus, config)`, then
  `bus.registerAndStartServices(IdentityService.class, RoutingService.class)` and
  `awaitRunning`
- `onStopping()` -> `Core.reset()`

**Skeleton:** registers `IdentityService` and `RoutingService`. The **target** also
registers `NotificationService` (status/event pub-sub — note `BaseService.updateStatus`
hard-routes status events to `ra.notification.NotificationService`), a read-only
legacy `DIDService`, the default I2P/Tor `ProtocolService`s, the Bitcoin services,
and the **localhost Envelope-JSON HTTP API** (`ra.http.EnvelopeJSONDataHandler` on
`127.0.0.1:2018`) that `1m5-desktop-java`'s `DesktopClient` already speaks — so
the desktop keeps working unchanged. That needs `resolvingarchitecture:http-client`.

---

## Android reuse (design intent — no Android code in this repo)

The pure-JVM constraint is what makes this possible. A later, separate effort would:

- make `1m5-android`'s `OneMFiveApplication` a thin host: one foreground `Service`
  that calls `Core.get().start(...)`, registers `IdentityService`, `RoutingService`,
  and Android `ProtocolService` adapters (embedded I2P router, `tor-android`);
- map Android `Payload` / `ServiceMessage` onto `Envelope` + `DynamicRoutingSlip`,
  and the Android `RetryStrategy` onto SEDA retry + envelope delay parameters;
- translate inbound `Intent`s to `Envelope`s at the host boundary;
- retire the duplicated `network.onemfive.android` router / identity / json code.

`1m5-android` is **not** touched by work in this repo.

---

## Configuration

`src/main/resources/1m5-core.config`, loaded via `ra.common.Config`. Everything is
overridable on the command line (`key=value`) or by a supplied `Properties`.

| key                         | meaning                                              |
|-----------------------------|-----------------------------------------------------|
| `1m5.version` / `.build`    | reported version                                     |
| `1m5.systemTimeZone`        | JVM default time zone (`UTC`)                        |
| `1m5.env`                   | `dev` \| `test` \| `prod`                            |
| `ra.sedabus.pool.max`       | worker count: an integer, or `Platform` (cores x 2) |
| `ra.http.server.configs`    | (staged) localhost API `name,path,host,port,handler` |

Base directory: `~/.1m5/core/`. Service state: `~/.ra/services/<FQCN>/`. Bus
dead-letter: `~/.ra/ra.servicebus.ServiceBus/deadLetter.json`.

---

## Known issues and decisions

- **Slip is LIFO.** `Envelope.addRoute` pushes. Use the one-hop-at-a-time router
  pattern; kept LIFO for `ra-common` compatibility.
- **`BaseRoute.fromMap` typo** — checks `"routedId"`, so `routeId` is never restored
  from JSON. External fix in `ra-common-java`.
- **`DynamicRoutingSlip` / `DequeStack` are not thread-safe** — fine; SEDA guarantees
  a single owner per stage.
- **Route JSON round-trip** needs a public no-arg constructor on every `Route` impl
  (`Class.forName(type).getConstructor().newInstance()`). Keep that contract.
- **`BaseService.updateStatus`** hard-routes status events to
  `ra.notification.NotificationService`; that service must be registered (target) or
  the route made configurable. `ServiceBus`'s own `ServiceStatusListener` path does
  not need it.
- **Envelopes can arrive at a service mid-startup** — the bus consumer is attached at
  registration, before `start()` runs. Services must tolerate early envelopes (or a
  readiness gate lands in `service-bus`, see its TODO).
- **secp256k1 availability** varies by JDK build; the skeleton `IdentityService`
  falls back to a placeholder id. Real BIP-340 needs a bundled secp256k1 lib.
