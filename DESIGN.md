# 1M5 Core — Design

## Context

1M5 began as a desktop application exploring one idea: communications and Bitcoin
activity should route intelligently across whatever network paths remain available,
instead of depending on a single channel. That routing model is the project's
strategic asset. It has been re-implemented several times with no shared code — a
thin JVM scaffold (`onemfive:platform`) and a complete Android-native version in
`1m5-android`.

`1m5-core-java` is the consolidation: one reusable, transport-agnostic core built on
the `resolvingarchitecture` bus libraries, able to run headless for
`1m5-desktop-java` today and to be hosted inside `1m5-android` later.
`1m5-core-rust` is a parallel, thinner implementation of the same design for
small-footprint / Redox hosts (`1m505`); the two are kept in step, and the
`CoreClient` contract below is the boundary that lets a host pick either one.

This document describes the target design of the **Java** implementation. It is the
implementation companion to `1m5-docs/architecture/README.md` (the mission
architecture — the layers, the `CoreClient` contract, the router target, the
per-language plan). A second implementation, `1m5-core-rust`, mirrors this one as a
thinner skeleton for small-footprint / Redox hosts.

Most of this is implemented (the bus stack, the service taxonomy, the router seam,
a `did-java`-backed `IdentityService` sealed at rest, and the **escalation router**
— ManCon × (web | P2P) network selection, address-matched transport choice,
cross-transport relay, hold + backoff retry); the rest is staged in
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

`1m5-core` depends on `service-bus` for the whole bus chain, and adds two direct
pins that the chain would otherwise supply at older versions:

- `resolvingarchitecture:common:1.3.0` — pinned above the `1.2.0` that
  `service-bus` / `seda-bus` / `i2p` ask for transitively (1.3.0 fixes `Signature`
  persistence and drops the unused `Reputation` stub).
- `resolvingarchitecture:did:1.3.0` — `did-java`, for the Nostr-compatible
  identity primitive (`ra.did.nostr`) and the read-only legacy OpenPGP keyring
  (`ra.did.openpgp`); `usb4java` (YubiKey) excluded.
- `resolvingarchitecture:i2p:1.7.1` and `resolvingarchitecture:tor-client:1.2.1` —
  wrapped by the default protocol services, registered only behind config flags.

<!-- -->

    1m5-core  ->  service-bus  ->  seda-bus  ->  common
              ->  common:1.3.0  (direct pin)
              ->  did:1.3.0     (direct pin)

    ra-common        resolvingarchitecture:common:1.3.0        (direct; Java 8)
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
hardened persistence). `1m5-core` gets that transitively; only `common` and `did`
are pinned directly, both to `1.3.0`, via plain `<dependency>` entries (no
`<dependencyManagement>`).

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

It then calls the pure decision engine and **pushes one transport hop** onto the
slip, sets ManCon-derived delivery parameters
(`setDelayed`/`setMinDelay`/`setMaxDelay`, `setCopy`/`setMinCopies`/`setMaxCopies`),
and returns. It does **not** ratchet — the engine does. If nothing can carry the
envelope it **holds** it and schedules a retry.

Route selection is **explainable** — the `SituationalAwareness` snapshot (band,
acceptable networks, ready networks, decision, chosen/relay network, attempt,
reason) is logged for every envelope.

### The escalation engine (`EscalationRouter`)

`RoutingService` is thin bus glue over a **pure decision function**,
`EscalationRouter.decide(...)` — no bus, no threads, so every branch is unit
tested (`EscalationRouterTest`, 12 cases). The ladder, ported from
`onemfive.routing.CRNetworkManagerService` and Remnant's `RouterService.Sender`:

1. **Clamp** the requested ManCon into the achievable band
   (`ManConStatus.select`). `RoutingService.refreshAvailability()` drives
   `maxAvailable` from the ready transports via `ManConNetworks.maxAvailableFor`
   (non-internet → NEO, I2P → VERYHIGH, Tor → HIGH, clearnet → LOW, nothing →
   NONE) and fires `ManConStatusListener`s on a change.
2. If the clamp cannot meet the operator's floor (`!meetsFloor`) → **HOLD** (wait
   for a better transport, never downgrade sensitive traffic).
3. **Acceptable networks** for the level (`ManConNetworks.acceptable`), most
   preferred first: LOW/MEDIUM → I2P/Tor by address; HIGH → I2P→Tor→non-internet;
   VERYHIGH → I2P only (Tor dropped) → non-internet; EXTREME/NEO → non-internet
   only. Web requests get the clearnet/overlay ordering.
4. **Direct**: the first acceptable network that is ready *and* that the
   destination has an address on (`PeerDirectory`). `localhost` URLs and
   undirected traffic take the first ready acceptable network.
5. **Relay**: the destination has an address on an acceptable-but-blocked
   network, and a relay peer is reachable on an acceptable one that *is* ready →
   push a `RelayedExternalRoute` (`fromPeer` = relay, `toPeer` = destination).
   Web requests relay through any peer on a ready acceptable network.
6. Nothing → **HOLD**.

**Hold + retry.** Held envelopes go into a map keyed by envelope id;
`RetryStrategy` (ported from Remnant: 20 s, constant for 20 min, ×2 backoff, 1 h
cap, 24 h give-up) schedules the next attempt through the `RetryScheduler` seam
(`ScheduledThreadPoolExecutor` in production, a manual driver in tests). The retry
re-submits the same envelope fire-and-forget; once the give-up window passes the
envelope is **dead-lettered** (error recorded, dropped from the hold queue).

**The two stack defects this depended on are fixed:** `ManConStatus.select()` no
longer collapses to `NONE` (see above), and `ra-common-java 1.3.2` fixed
`BaseRoute.fromMap`'s `"routedId"` typo so `routeId` survives slip JSON
round-trip.

**Still not built** (see [`TODO.md`](TODO.md)): the random-delay *ratchet* beyond
the fixed VERYHIGH/EXTREME/NEO parameter bands, NEO mnemonic-only keys,
timestamped per-*level* connectivity probing (the current `maxAvailable` is a
transport-class heuristic), the reliability-scored `ra.networkmanager` peer
store, inbound dedupe, and live two-node relay testing.

The mission-level statement of this design is in
`1m5-docs/architecture/README.md` §"The router — target design"; `1m5-core-rust`
mirrors this engine (`escalation::decide`).

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

A `DataService` owning the **node** identity (user identities and contacts are P2).

The node identity is a **Nostr-compatible** secp256k1 keypair, derived through
`did-java`'s `ra.did.nostr` — there is no JCA fallback and no placeholder id:

- `NostrKeyRing` (ACINQ `secp256k1-kmp` / libsecp256k1) does key generation,
  x-only public-key derivation, and BIP-340 signing. `keyRing.init(props)` loads
  the backend; if it fails the service logs and stays up (the daemon does not go
  down over it) but `hasNodeSecret()` is false.
- Encodings: 32-byte secret, x-only BIP-340 public key, lowercase hex, `npub`,
  `did:nostr:<hex>` — exposed as `getNodePublicKeyHex()` / `getNodeNpub()` /
  `getNodeDid()`.

**At rest.** `IdentityService` sets a `NostrIdentityStore` on
`<serviceDir>/identity/`. The passphrase is resolved from the `1m5.pass` config
key first, then the `1m5.pass` environment variable (the `Daemon` warns once when
neither is set):

- **passphrase present** — the secret is sealed (Argon2id + AES-256-GCM) into
  `identity/<pubkey>.json`; `node.pub` holds the public key as a pointer.
- **no passphrase** — the node still gets a real identity, but the secret is
  written unencrypted to `node.sec` with a warning.
- **upgrade in place** — the first start with `1m5.pass` set migrates a `node.sec`
  plaintext secret into a sealed file automatically.
- If a sealed file exists but no passphrase is available, the node loads its
  **public** identity only (can verify, cannot `signAsNode`).

**API.** `signAsNode(NostrEvent)` signs in place with the node key (requires
`hasNodeSecret()`); the language-agnostic `CoreClient` form is
`byte[] signAsNode(byte[] canonicalEvent)`. Over the bus, `handleDocument` with
operation `GET_NODE_IDENTITY` returns the public summary only — the secret never
travels on the bus.

**Legacy OpenPGP** (`ra.common.identity.DID` + `ra.did.openpgp`) stays available
for **read-only** verification of old contacts and proofs. There is **no
migration code path** — 1M5 has not been marketed and has no user identities to
migrate.

**Key domains stay separate** — messaging identity, encryption, wallet, transport,
device, session. Bitcoin wallet keys must never automatically become messaging
identities. Any shared-seed derivation needs explicit, documented domain
separation.

**P2 (see [`TODO.md`](TODO.md)):** user identities distinct from the node
identity; the full key-domain separation; contact model + trust states; the E2EE
envelope (secp256k1 ECDH + HKDF-SHA256 + an AEAD); a threat-model pass before the
identity + encryption design is called stable.

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

## The embedding contract (`CoreClient`)

`Core.get().start(Properties)` + `registerServices` / `awaitServices` / `send` is a
usable embedding path for a JVM host, but it is **not** language-agnostic:
`registerService(Class<…>)` instantiates reflectively and keys channels by FQCN,
and `ra.common.Envelope` carries a polymorphic `DynamicRoutingSlip` of `Route`
subclasses round-tripped through `Class.forName`. A Rust core cannot satisfy that,
and the Rust core has already diverged (string-keyed services, a flat `Envelope {
id, to, sender, headers, payload, slip: VecDeque<String>, attempts }`).

So the host↔core boundary is a **narrow, language-agnostic facade** in a new
zero-dependency module, `network.onemfive:1m5-core-client:0.1.0`. Nothing in it
names a Java class, a JVM type, or a bus primitive. The mission-level statement is
`1m5-docs/architecture/README.md` §"The 1M5 Core contract"; it must read
identically there, here, and in `1m5-android/DESIGN.md`.

**Verbs — `CoreClient` (~8):**

| Verb | Purpose |
|---|---|
| `start(Map<String,String> config)` | boot the core with a flat string config |
| `stop()` | shut the core down |
| `send(Msg)` | fire-and-forget outbound |
| `send(Msg, ReplyHandler)` | outbound with a completion callback |
| `CoreInbound registerProtocol(ProtocolHandle)` | register a transport the host owns; returns the sink the core calls for inbound payloads |
| `awaitReady(long timeoutMs, String... channels)` | block until named channels are ready |
| `List<TransportStatus> readyTransports()` | which transports report ready |
| `IdentityStatus identityStatus()` | node identity summary (public id only) |
| `byte[] signAsNode(byte[] canonicalEvent)` | BIP-340 sign with the node key |

**The flat envelope — `Msg`:**

    Msg {
      String              id
      String              to          // channel name or peer address
      String              sender
      Map<String,String>  headers     // routing scalars live in reserved x.* keys
      byte[]              payload
      Deque<String>       slip        // channel names, front = next hop
      int                 attempts
    }

Routing scalars are carried as reserved `x.*` headers rather than typed fields:
`x.sensitivity` (0–10), `x.url`, `x.serviceLevel`, `x.delayed` / `x.minDelayMs` /
`x.maxDelayMs`, `x.copy` / `x.minCopies` / `x.maxCopies`,
`x.dest.{i2p,tor,bt,peerId}`, `x.relay.peerId`, `x.error.*`.

**Transport registration — callback-shaped:**

    ProtocolHandle {            // the host implements this
      String          name()   // stable channel name, e.g. "I2P"
      Network         network()
      TransportStatus status()
      boolean         send(Msg) // carry it out over this transport
    }

    CoreInbound {               // the core returns this
      void accept(Msg)          // host calls it for every inbound payload
    }

**Addressing is by stable string channel name**, never by `Class<?>`.
`ProtocolService` gains a `channelName()`; `RoutingService.choose()` routes on it
instead of `getClass().getName()`; `Core` keeps a `name → service` alias table (no
`service-bus` change).

### `EmbeddedCoreClient` (in this module)

`EmbeddedCoreClient implements CoreClient` sits over `Core.get()` and does the
`Msg` ↔ `ra.common.Envelope` translation:

- `Msg.headers` `x.*` → `Envelope` scalar setters (`setSensitivity`, `setURL`,
  `setDelayed`/`setMinDelay`/`setMaxDelay`, `setCopy`/…); everything else stays in
  the `Envelope` headers map.
- `Msg.slip` is **front-to-back** (front = next hop); it is reversed into the
  `ra` LIFO `DynamicRoutingSlip` stack internally. The Rust `VecDeque` already
  matches the contract's order.
- `registerProtocol(handle)` wraps the `ProtocolHandle` in a synthetic
  `HandleBackedProtocolService` (`channelName()` = `handle.name()`) and registers
  it on the bus; the returned `CoreInbound` feeds inbound `Msg`s back through the
  producer.

### Other implementations satisfy the same interface

- **`HttpCoreClient`** — talks to a node's localhost RPC API (ADR-0003). `Msg`'s
  JSON encoding *is* that API's envelope encoding, so this is a thin transport
  shim.
- **`RustCoreClient`** — a UniFFI binding over `1m5-core-rust` packaged as an
  `.aar`; `ProtocolHandle` / `CoreInbound` become UniFFI callback interfaces.

An abstract `CoreClientContractTest` runs against `EmbeddedCoreClient` now and the
others later; JSON golden files are shared with `1m5-core-rust` and follow the
`did-vectors` fixture style. See ADR-0003 for the node/client split.

## Android reuse

The pure-JVM constraint makes an in-process Android core possible, but the plan
lives in **`1m5-android/DESIGN.md` §"1M5 Core Integration (Target Architecture)"**,
which is authoritative for how Remnant adopts this core (`:core-host` module, the
`I2PProtocolAdapter` / `TorProtocolAdapter` that wrap Remnant's existing embedded
transports, the `OneMFiveApplication` compatibility shim, the service-by-service
cutover). That work changes no code in this repo and no code in `1m5-android`
during the current design pass.

The invariant this repo owns: the core stays **pure JVM** (no `android.*`);
`ProtocolService` adapters are per-platform; the host translates its own message
type (`Intent` / `Payload`) to `Msg` at the `CoreClient` boundary.

## Dependency distribution

The `resolvingarchitecture:*` and `network.onemfive:*` libraries are **not
published to registries**. They are built from local sibling source repos and
pulled into consumers at build time.

- Provide `tools/build-ra-libs.sh`: build the sibling repos in dependency order
  (`ra-common-java` → `seda-bus-java` → `service-bus-java` → `did-java` →
  `i2p-java` / `tor-client-java` → `1m5-core-java`) and `mvn install` each to the
  local Maven repo, plus a `DEPENDENCIES.md` recording the order and versions.
- Consumers (including `1m5-android`) add `mavenLocal()` and depend on `1m5-core`
  as a normal `implementation` — its Maven-Central transitives
  (`secp256k1-kmp-jni-*`, net.i2p, tor) resolve normally. This is preferred over
  hand-curating a `src/dist` jar closure.
- The `1m5-android` `src/dist` `fileTree` slot is reserved for artifacts that are
  not Maven modules — notably a future `1m5-core-rust` `.aar`.

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
  pattern; kept LIFO for `ra-common` compatibility. The `CoreClient` `Msg.slip` is
  front-to-back and `EmbeddedCoreClient` reverses it into the stack.
- **~~`ManConStatus.select()` always returns `NONE`~~ — fixed.** `maxAvailable` is
  now driven from ready transports (`RoutingService.refreshAvailability()` →
  `ManConNetworks.maxAvailableFor`), listeners fire on change, and `meetsFloor()`
  lets the router hold rather than downgrade. (The probe is a transport-class
  heuristic, not yet timestamped per-level tests.)
- **~~`BaseRoute.fromMap` `"routedId"` typo~~ — fixed** in `ra-common-java 1.3.2`
  (also `TextMessage.toMap`/`fromMap`); this repo pins `common:1.3.2`.
- **No `ServiceLevel` is set on any channel.** `IdentityService` (a `DataService`)
  and every other service run on a non-durable `AtMostOnce` channel; a crash mid
  routing-slip loses the envelope. `DataService` channels should register at
  `AtLeastOnce`/`ExactlyOnce` (see the taxonomy section) — not yet wired.
- **`DynamicRoutingSlip` / `DequeStack` are not thread-safe** — fine; SEDA guarantees
  a single owner per stage.
- **Route JSON round-trip** needs a public no-arg constructor on every `Route` impl
  (`Class.forName(type).getConstructor().newInstance()`). Keep that contract.
- **`BaseService.updateStatus`** hard-routes status events to
  `ra.notification.NotificationService`; that service must be registered (target) or
  the route made configurable. `ServiceBus`'s own `ServiceStatusListener` path does
  not need it.
- **Envelopes can arrive at a service mid-startup** — the bus consumer is attached at
  registration, before `start()` runs. `ServiceBus.registerService` also starts
  services on an `AppThread`, so registration order does not guarantee start order.
  Services must tolerate early envelopes (or a readiness gate lands in
  `service-bus`, see its TODO).
- **secp256k1 is now a hard dependency**, not JDK-provided: `did-java` brings ACINQ
  `secp256k1-kmp` (JNI). The old JCA-or-placeholder fallback is gone. An Android
  host swaps the JNI artifact for `secp256k1-kmp-jni-android` (see the dependency
  notes in `1m5-android/DESIGN.md`).
