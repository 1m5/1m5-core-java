# 1M5 Core (Java)

`network.onemfive:1m5-core` — the intelligent, censorship-resistant router at the
centre of 1M5.

1M5 lets messaging, Bitcoin payments, and network traffic keep moving when ordinary
internet paths are blocked, monitored, degraded, or unavailable. Applications hand
the core an envelope; the **router** decides how to get it there — across Tor, I2P,
Bluetooth, or whatever transport is currently reachable — and adapts as paths fail.

This module is the reusable core: the bus wiring, the service model, the router, and
the identity/ManCon models. It is a plain JVM library with no UI and no
platform-specific dependencies, so the same core can run as a headless daemon (for
`1m5-desktop-java`) and, in time, inside `1m5-android`.

> **Status: early.** This is a working skeleton plus the design. The bus stack, the
> service taxonomy, the router seam, and a green test suite are in place. The full
> router escalation logic, the ADR-0002 identity work, the localhost HTTP API, and
> the default I2P/Tor transports are staged in [`TODO.md`](TODO.md).

## Architecture in one picture

One dependency chain, declared as one dependency (`service-bus`):

    1m5-core         (network.onemfive:1m5-core)      <-- this module
      service taxonomy (business / data / protocol),
      the 1M5 RoutingService, ManCon model, identity service,
      Daemon (extends ra.servicebus.Daemon) + Core runtime handle
        | depends on
    service-bus-java (resolvingarchitecture:service-bus:1.5.0)
      service lifecycle + discovery: register / start / awaitRunning,
      getService / findRunningServices, pause / restart, per-service
      status, control-command envelopes, reusable Daemon base
        | brings
    seda-bus-java    (resolvingarchitecture:seda-bus:1.3.1)
      staged bounded-queue bus, event-driven worker pool, retry +
      dead-letter, durable replay -- AND the routing-slip execution engine
        | brings
    ra-common        (resolvingarchitecture:common:1.2.0)
      Envelope, DynamicRoutingSlip, Route, BaseService, ServiceLevel

Every service is one of three kinds:

- **Business** — internal logic and orchestration (messaging, wallet, escrow, the
  router itself). Owns no durable state.
- **Data** — internal persistence (identity vault, peer directory, contacts).
- **Protocol** — an external transport adapter (I2P, Tor, HTTP, Bluetooth, …),
  implementing a common `Transport` contract. Adapters are per platform.

Services orchestrate each other by pushing hops onto the envelope's
`DynamicRoutingSlip`; the SEDA bus walks the slip. See [`DESIGN.md`](DESIGN.md) for
the full picture.

## Build

Requires **JDK 17+** and Maven 3.6+. 1M5 Core targets Java 17 (matching the
`1m5-android` app module); the bus libraries below it are Java 11.
`1m5-desktop-java` stays on Java 11 via a small `1m5-common` rather than depending on
this jar directly — see [`TODO.md`](TODO.md).

The `resolvingarchitecture` bus jars must be in your local Maven repo —
`service-bus:1.5.0` and what it brings (`seda-bus:1.3.1`, `common:1.2.0`). Build them
from the sibling source repos in order:

    cd ../../ra-common-java   && mvn -DskipTests install   # common:1.2.0
    cd ../seda-bus-java       && mvn -DskipTests install   # seda-bus:1.3.1
    cd ../service-bus-java    && mvn -DskipTests install   # service-bus:1.5.0

Then, in this directory:

    mvn test        # CoreSmokeTest + RoutingServiceTest
    mvn package     # target/1m5-core-0.1.0-jar-with-dependencies.jar

## Run the daemon

    1m5.pass=<passphrase> java -jar target/1m5-core-0.1.0-jar-with-dependencies.jar

Starts the service bus, establishes the node identity, and registers the router.
Base directory is `~/.1m5/core/`.

Add `1m5.i2p.enabled=true` (arg or config) to also register the I2P protocol
service — an `i2p-java` embedded router; first start reseeds and takes minutes.
`ra.i2p.mode=local` attaches to a router already running on this host instead.

The localhost Envelope-JSON API that `1m5-desktop-java` speaks is not wired yet
(see [`TODO.md`](TODO.md)).

## Embedding

    Core.get().start(properties);                       // builds + owns a ServiceBus
    Core.get().registerServices(RoutingService.class, MyBusinessService.class);
    Core.get().awaitServices(10_000, RoutingService.class);
    Core.get().send(envelope);                          // fire and forget
    Core.get().send(envelope, replyHandler);            // with a completion callback

Service management (register / start / discover / await / pause) is
`service-bus`'s `ServiceBus` — reachable via `Core.get().bus()`. `Core` adds the
1M5-specific runtime state (`manConStatus()`, `readyProtocols()`).

## Relationship to the other 1M5 repos

| Repo                | Role                                                                              |
|---------------------|----------------------------------------------------------------------------------|
| `1m5-core-java`     | this module — the reusable router and bus stack                                   |
| `1m5-desktop-java`  | JavaFX client; talks to a running core daemon over a localhost Envelope-JSON API  |
| `1m5-android`       | product "Remnant"; today a standalone reimplementation, a future host for this core |
| `1m5-docs`          | `ARCHITECTURE.md`, ADRs (incl. ADR-0002 Nostr identity), roadmap, strategy         |

`1m5-android` and `1m5-desktop-java` are **not** modified by work in this repo; their
adoption of this core is deferred roadmap material in [`TODO.md`](TODO.md).

## Licensing

Copyright Unrecognized. See `1m5-docs` for project licensing direction.
