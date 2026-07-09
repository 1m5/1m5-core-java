[![Build Status](https://travis-ci.com/1m5/1m5.svg?branch=master)](https://travis-ci.com/1m5/1m5)

# 1M5

A secure open-source decentralized censorship-resistant peer-to-peer network router service with end-to-end encryption
and anonymity as a base layer for creating easy to build and use secure decentralized peer-to-peer
applications requiring no server connections that can be used around the world by any person looking
to protect their communications and personal data from unethical monitoring, interception, intrusion,
and censorship.

## Version

2.6

## Licensing

Copyright Unrecognized


## Authors / Developers

* objectorange (Brian Taylor) - [GitHub](https://github.com/objectorange) | [LinkedIn](https://www.linkedin.com/in/decentralizationarchitect/) | objectorange@1m5.io PGP: DD08 8658 5380 C7DF 1B4E 04C2 1849 B798 CF36 E2AF | brian@resolvingarchitecture.io PGP: 2FA3 9B12 DA50 BD7C E43C 3031 A15D FABB 2579 77DC
* evok3d (Amin Rafiee) - [Site](https://arafiee.com/) | PGP: D921 C2EE 60BA C264 EA40 4DC5 B6F8 2589 96AA E505
* azad (Erbil Kaplan) - [LinkedIn](https://www.linkedin.com/in/erbil-kaplan-b8971b18/) | PGP: 2EBC 2239 E9B8 2BCA 7176 77FE FD80 A0C2 95FD EBAC
* z0??0z
* Global group of independent pseudo-anonymous developers

## Opportunities

[**Freedom of Speech**](https://en.wikipedia.org/wiki/Freedom_of_speech) - a principle that supports the freedom of
an individual or a community to articulate their opinions and ideas without fear of retaliation, censorship,
or sanction. The term "freedom of expression" is sometimes used synonymously but includes any act of seeking,
receiving, and imparting information or ideas, regardless of the medium used.

[**Censorship**](https://en.wikipedia.org/wiki/Censorship) - the suppression of speech, public communication,
or other information, on the basis that such material is considered objectionable, harmful, sensitive,
politically incorrect or "inconvenient" as determined by government authorities or by community consensus.

Constraining the free flow of information between people is a direct threat to our freedom and censorship of
communications on-line is growing world-wide.

- https://internetfreedomwatch.org/timeline/
- https://www.wired.com/2017/04/internet-censorship-is-advancing-under-trump/
- https://rsf.org/en/news/more-100-websites-blocked-growing-wave-online-censorship

On-line communications are censored at the point of entrance by Internet Service Providers (ISP).
They act as gateways to the internet providing governments control over speech by having the
ability to restrict usage and track people's usage via their leased IP addresses. In order to make tracking usage much more
difficult, tools have come out that provide techniques called onion-/garlic-routing where the source and destinations of
internet routes can not be determined without breaking encryption, a very expensive feat, sometimes impossible today when
considering the encryption algorithms used.

Two primary tools today that support this are Tor and I2P. Tor provides a browser
that makes it easier to use while I2P is much less known. Both are complementary in that Tor was designed for browsing
today's current web sites anonymously. I2P was designed for peer-to-peer communications within I2P. Neither have good
APIs for developers to embed in their products making uptake slow for many applications.

A third tool on the horizon is one that completely circumvents ISPs by not using them. They're called direct wireless
mesh networks and they can communicate directly device-to-device using technologies such as WiFi Direct. Firechat is an
example used during the 2014 Hong Kong protests after the Chinese government threatened to shutdown the internet in that
area. New mesh solutions are popping up including RightMesh that seek to improve on earlier designs. But the technology
is still in its infancy and needs to be pulled into ever day applications more easily once they've matured.

Even getting these technologies in wide use doesn't solve the problem of online censorship. People in governments, corporations, and
other thieves are constantly finding ways to circumvent these technologies to censor and steal information.

In addition:

- Most organizations today (e.g. Tech, Banks, Governments, Hospitals) track, persist, and use our behavior for their profit not ours.
- Centralized organizations are major targets for theft.
- Closed source software can easily contain hidden back doors for thieves to access our information without our knowledge and many open source applications have closed source libraries embedded in them.
- Whistleblowers, the abused, visible minorities, and a myriad of other people could be emboldened by anonymity to speak out in a manner that would otherwise be unavailable if they were forced to identify themselves.
- Decentralized applications and cryptocurrencies like Bitcoin are helping to wrestle some control from centralized organizations although they are largely used on servers and distributed ledgers are still logically centralized and difficult to maintain anonymity at the network layer.
- Smartphone ownership around the world is greater than PC ownership.
- Smartphones, our primary means of global communication and collaboration, are weak in maintaining our anonymity and privacy - critical to ensuring individual freedom.

## Solution

1M5 works to solve these issues by providing three primary benefits.

1. Intelligent Censorship-Resistant Anonymous Router embedding Tor, I2P, Direct Wireless Ad-Hoc Networks, and other
networks, using them intelligently as one dynamic censorship-resistant, end-to-end encrypted, anonymous mesh network.
2. Offers access to commonly desired decentralized services in an anonymous fashion including
self-sovereign decentralized identities (DID), Bitcoin, and other privacy preserving services.
3. Provides easy to use APIs for developers to embed in their applications to facilitate up-take.

### Features

#### General
* **Not a Company or Centralized Corporation**: 1M5 repeatedly emphasizes the fact that it isn’t a company. It’s just code. 1M5 is not incorporated, and it’s not centralized in any location. It’s a decentralized mission that cannot be shut down. As long as at least two peers are running the 1M5 software, two people can communicate with 1M5.
* **Open Platform**: Runs as a Java service that can be deployed to any environment supporting Java 1.8.
* **Open Source**: source code is fully available for anyone to check online.
* **P2P Exchange**: its peer-to-peer network includes everyone running 1M5 software. You connect to this P2P network automatically when starting up 1M5 service.
* **Tor Support**: automatically connects to the Tor network on startup and all requests to HTTP sites are encrypted end-to-end and routed over Tor.
* **I2P Support**: automatically connects to the I2P network on startup and all peer-to-peer requests are encrypted end-to-end and routed over I2P.
* **Bluetooth Support**: automatically connects when Bluetooth is discovered to be turned on. Is used when Tor and I2P are not available.
* **Private**: Nobody except individual users can exchange personally identifying data and it can only be done voluntarily - code is not allowed to do it without end users permission. All personal data is stored locally on a drive that you bring or locally on your computer if you do not bring any.
* **Decentralized**: 1M5’s P2P network is decentralized with no single point of failure. Unlike centralized applications that can be shut down, 1M5 is a peer-to-peer network distributed and decentralized around the world.

#### DEX
* **Non-Custodial**: the DEX never holds your funds. It contains a non-custodial exchange platform supporting the simple swapping of Bitcoin using 2-2 multisig Bitcoin escrows with other users.
* **Self-arbitration system**: DEX arbitration relies on both parties to resolve otherwise no resolution takes place.
* **Security Deposits**: No security deposits required as the precludes people from initial usage.
* **No Marketplace Trading**: to simplify the exchange, market prices determined from average of external pricing sources, no trading supported.
* **Automatic Order Matching**: the DEX automatically matches requests speeding up transactions.
* **Mobile Apps**: Android in progress, Linux Mobile in future.
* **Out Of Band Fiat Payments**: The DEX does not directly integrate with banks or other national currency payment systems in any way. Instead, the DEX’s trading protocol allows buyers and sellers to settle fiat payments outside the DEX application (say, by completing a bank transfer).
* **Buy Crypto With Bank Transfers**: Because the DEX arranges out of band fiat payments, the DEX can be used to buy crypto with bank transfers. Depending on your national currency, the DEX will be able to find requests from people willing to accept national bank transfers in exchange for bitcoin, e.g. US the DEX can find sellers willing to use Zelle, for example, while for Europeans it will find Revolut requests.
* **No Registration or Identity Verification Required**: the DEX lets you trade Bitcoin with no registration or centralized identity verification required. Unlike a centralized exchange, you don’t have to verify your identity with some centralized server.
* **Escrow System with 2-of-2 Multi-Sig**: the DEX uses an escrow system that locks funds in a 2-of-2 multi-signature address. This ensures that the exchange participants need to approve the transaction for funds to be released.
* **Transaction Fees Go to Developers**: the DEX’s transaction fees go to developers to further build and maintain the application.
* **Cheap Fees**: fees are necessary for protection against request spam, market manipulation and identity harvesting. It costs a small amount of BTC to make an exchange request, the only fee you pay with 1M5.
* **Available Anywhere in the World**: the DEX is anonymous exchange software that uses the I2P protocol. It can be used anywhere in the world. However, 1M5’s developers recommend using caution if exchanging bitcoin in parts of the world where bitcoin is illegal. Ensure the 1M5 situational awareness is set to the appropriate level of expected threat.
* **Exchange Limits**: the DEX applies limits of BTC per trade to reduce the risk of unethical activity on the platform. However, these limits could be raised in the future. Certain payment methods may also have lower limits. Generally, payment limits on the DEX are quite low to discourage use of the platform for unethical activity.

### Routing

We provide a Maneuvering Condition (ManCon) setting to signal what level of maneuvering is likely required to prevent censorship.
This should be set by the end user based on their circumstances. They should also be made aware of recommended ManCon
levels for the jurisdiction they are currently in. These ManCon levels are largely based on [Press Freedom Index](https://en.wikipedia.org/wiki/Press_Freedom_Index)
and updated on the [ManCon page](https://1m5.io/mancon.html).

Setting the ManCon level manually by the end user informs the codebase what based level of ManCon should be used although
final ManCon is determined by blocks encountered during routing and thus how to ratchet up resistance as these blocks occur.

For developers using the API, all requests for services, e.g. Bitcoin, require an Envelope with a ManCon level set. This ManCon level decides
what base level of privacy is desired. Options are Low, Medium, High, Very High, Extreme, and Neo.
All P2P communications use High ManCon as the default resulting in the use of I2P with latency expectations between 200 milliseconds and 2 seconds.
This is the default setting in the Envelope.

When making web requests, remember to set the appropriate ManCon level otherwise all web requests will use the HIGH ManCon
level thereby routing all web requests through the I2P layer to 1M5 nodes with Tor enabled resulting in higher than might be
expected latencies yet very censorship-resistant and private page views. This is the ideal setup for people in China as an example wishing
to view web pages globally without censorship and without getting a knock on their door where Tor is getting heavily blocked.

#### LOW - MANCON 5

Open/normal SSL based communications with no expected censorship or privacy intrusion attempts is the norm.

Examples: Norway, Iceland, Costa Rica, Jamaica, Ireland

* Web: I2P used for .i2p addresses and Tor for other web requests including .onion addresses. If that fails, it will be assumed that the site is down.
* P2P (Messenger): I2P used unless found to be blocked. Then Tor will be used as a tunnel to a peer that has I2P enabled.
If Tor blocked, will ratchet up to 1DN for assistance.

Expect latencies of 500 milliseconds to 2 seconds unless 1DN is needed.

#### MEDIUM - MANCON 4

Normal censorship attempts by states on reading news (public web sites getting blocked, government shutdown of cloud cdn content).
Many moving towards using Tor and/or VPNs although no fear of circumventing censorship attempts.

Examples: Australia, United States, France, United Kingdom

* Web: Tor will be used. If that fails, the request will be forwarded to other peers until a peer can make the request
returning the result directly back to the requesting peer. If those fail, it will be assumed that the site is down.
* P2P unchanged

Expect latencies of 500 milliseconds to 4 seconds unless 1DN is needed.

#### HIGH - MANCON 3

Tor and VPNs are beginning to get blocked. Many beginning to move to I2P. Some self-censorship likely. This is the default setting for 1M5.

Examples: Brazil, Greece, Poland, Panama, Nicaragua

* Web: will use an I2P peer that has access to Tor to make the request.
* P2P unchanged

Expect latencies of 4-10 seconds.

#### VERYHIGH - MANCON 2

I2P is getting attacked slowing the network and people are beginning to get threatened for circumventing censorship attempts resulting in self-censorship.

Examples: Mexico, Venezuela, Russia, India, Turkey

* Web: will use I2P with random delays to forward all requests to a 1M5 peer with Tor access at a lower ManCon. If both I2P and
Tor blocked at end user, 1DN will be used to find a 1M5 peer at a lower ManCon to fulfill the request.
* P2P: will use I2P with random delays of 4-10 seconds. If I2P gets blocked, will attempt to use Tor as a tunnel. If that is blocked,
1DN will be used.

Expect latencies of 6-16 seconds unless 1DN used which could result in very large latencies where only asynchronous messaging
(e.g. Email) and asynchronous web requests are plausible.

#### EXTREME - MANCON 1

Internet has been blocked for end user, e.g. local cellular service towers shutdown or provider turns off access and/or
threats of imprisonment and/or death are made to induce self-censorship with actual evidence of enforcement.

Examples: China, North Korea, East Turkestan, Iran, Saudi Arabia, Iraq, Egypt

* Web: 1DN will be used to forward requests to Tor users with a lower ManCon to fulfill the request.
* P2P: 1DN peers will be used until a peer with I2P access can route the request.

Expect wide-ranging latencies.

#### NEO - MANCON 0

Whistleblower with deep state top secrets or investigative journalist with life-threatening information.

* Web: 1DN is used to forward requests to a peer that will then request another peer using I2P with high delays to make the Tor request.
* P2P: 1DN is used to forward a message through a random number and combination of 1DN/I2P peers at random delays of up to 90 seconds
at the I2P layer and up to 3 months at the 1M5 layer. A random number of copies (3 min 12 max) of the message are sent out
with the end user having a 12 word mnemonic passphrase as the only key to the data.

Wide-ranging latencies but highest privacy and censorship-resistance.

## Design

1M5 is composed of a Service-Oriented Architecture (SOA) design using a minimalistic service bus for services,
a Staged Event-Driven Architecture (SEDA) design for asynchronous multi-threaded inter-service communications,
a service registry, internal core services, and Censorship-Resistance Network and Peer Manager Services
for advanced intelligent interaction with peers.

### Core Services

* Censorship-Resistance Network Manager Service
* Censorship-Resistance Peer Manager Service
* Enterprise Applications Integration (EAI) Routing service for providing data-driven inter-service routing patterns
* Key Chain Service with key management and encryption/decryption features
* Decentralized IDentification (DID) Service for authentication/authorization and reputation building (Web of Trust)

### Invisible Direct Network (1DN)

The 1DN is the CR Network and Peer Manager Services working together with the Tor, I2P, Bluetooth, WiFi, Full-Spectrum
Radio, and LiFi Network Services to ensure your communications are not blocked.

## Implementation

The application is written in Java using JDK 1.8 although some services may support older versions. It is built though
with OpenJDK 11, the recommended JDK version.

## Roadmap
* Upgrade Neo4J

## ManCon Status
ManCon Status is provided by three parameters: Minimum Required, Maximum Available, and Maximum Supported.

* Minimum Required: Set by end user or configuration to state the minimum exceptable ManCon to be used. Changed by end user manually or, in the future, by automated situational awareness.
* Maximum Available: Real-time updated parameter providing the maximum ManCon currently achievable based on CR Network Manager findings.
* Maximum Supported: Maximum ManCon able to be reached based on what network services were registered.

An example visual:

```
None    HTTP    Tor     I2P     [Non-Internet: BT Mesh, WiFi, Satellite, FS Radio, LiFi]
-------------------------------------------------------------------------------------------
                 |       |      |
                MR      MA      MS
```
In the example above, the end user is specifying that Tor is the minimum that should always be used.
I2P is currently the highest we can achieve in ManCon.
Bluetooth Mesh is the highest possible ManCon (but not currently the Max Available as it's not connected to the network).

## Censorship-Resistance Routing
1. If I'm being blocked on Tor, use I2P.
1. If I'm being blocked on I2P, use Tor.
1. If I'm being blocked on both Tor and I2P or the local cell tower is down, use Bluetooth Mesh.
1. If I'm being blocked on both Tor and I2P or the local cell tower is down, and the peer I'm trying to reach is currently accessible via Bluetooth Mesh, use Bluetooth Mesh to connect to a Peer that is able to connect to the peer desired using Tor/I2P.
1. If Bluetooth Mesh is not available but WiFi-Direct is, use it instead.
1. If Bluetooth and/or WiFi-Direct are not available (e.g. being locally jammed), and a LiFi receiver is available, use it to get out.
1. If no LiFi receiver is available, use Full-Spectrum Radio to attempt to reach an online 1M5 user.

| Service                | Connecting | P2P   | Discovery | Relaying | Notes
| :--------------------- | :--------: | :---: | :-------: | :------: |
| Tor Client             | Y          | Y     |           |          | P2P accomplished using Hidden Services
| I2P Client             | Y          | Y     | Y         |          |
| Bluetooth Mesh Client  | Y          |       |           |          | Only direct P2P supported using v4 Bluetooth; v5 Mesh on roadmap
| WiFi Client            |            |       |           |          |
| FS Radio Client        |            |       |           |          |
| LiFi Client            |            |       |           |          |
| Bitcoin Client         | Y          |       |           | N/A      | P2P and Discovery means Bitcoin can use 1M5 inter-node
| Monero Client          |            |       |           | N/A      | P2P and Discovery means Monero can use 1M5 inter-node

## Relay Routing
Relays are used to route to networked peers when a requested network is unavailable yet other networks are. For example,
trying to use a browser to view a site using Tor and the local client is unable to connect to the Tor network yet can
connect to I2P, I2P will be used to relay the request to another peer who has access to the Tor network. This also goes
for Bluetooth. If a client no longer has access to the internet, e.g. forgot to pay the bill, and thus unable to view
that web page using Tor as no internet connection is available, and another peer with 1M5 is running within bluetooth range,
has access to the internet via Tor, and both the client and peer have bluetooth enabled, the client's 1M5 instance will
send the Tor request to the nearby peer using Bluetooth and that peer will make the Tor request for the resource returning
the result back to the originating peer (client).

This behaviour is available to all other applications. For example, you have a bitcoin wallet that originally pointed to
a specific bitcoin node using Tor yet are in a jurisdiction where Tor is actively being blocked (entrance/exit nodes getting IP blocked),
by pointing your bitcoin wallet to the local 1M5 OS service, 1M5 will use I2P to route to a peer with Tor available using
that node to make the Tor request to the specified Bitcoin node. If cellular access is not available yet a local 1M5 peer
has both Bluetooth available and Tor/I2P access, that peer will be used through bluetooth to make the request.

Additional networks are coming including an upgrade Bluetooth mesh network where bluetooth itself can propagate requests
through chained bluetooth networks until a peer with 1M5 running is located to complete requests on other networks.

* Tor-Tor (TT): To avoid timing attacks when using Tor, use two Tor circuits in sequence with a 1M5 peer between them providing a random delay out and in.
* I2P-Tor (IT): When desiring to access a web site and Tor is blocked, use I2P to a 1M5 user with Tor not blocked to make the request.
* BT-Tor (BT): When cellular access is not available and requesting a web site, use Bluetooth to a 1M5 user with Tor not blocked to make the request.
* BT-I2P (BI): When cellular access is not available and request a P2P message with a peer with I2P available but not Bluetooth available, use
  Bluetooth to get to a Peer with both I2P and Bluetooth available as a relay to the peer with I2P available but not Bluetooth.

| Route |    Description    | Implemented | Tested | Production | Vulnerabilities |
| :---: | :---------------: | :---------: | :----: | :--------: | :-------------: |
| TT    | Tor-Tor           |             |        |            |                 |
| IT    | I2P-Tor           |             |        |            |                 |
| BT    | BT-Tor            |             |        |            |                 |
| BI    | BT-I2P            |             |        |            |                 |

## Fund Raising

1M5 is supported entirely through gifting and volunteers. No funds from investors have been nor will be taken.

## Threats & Counter Measures

Censorship attempts can be made in a myriad of ways and are ever-changing as technology changes and attackers grow in experience.
Below are some of these methods and how 1M5 and composite networks mitigate them.

### DNS Blocking

Inbound and outbound blocking of IP addresses by DNS servers.

Resources
* [Wiki](https://en.wikipedia.org/wiki/DNS_blocking)

#### I2P
Does not use DNS.

#### TOR
Does not use DNS.

#### Bluetooth
Does not use DNS.

#### WiFi-Direct
Does not use DNS.

#### Full Spectrum Radio
Does not use DNS.

##### LiFi
Does not use DNS.

#### 1M5
Does not use DNS.


### DNS Poisoning

Corruption in a DNS server's resolver cache by swapping out valid IP addresses with invalid addresses resulting in traffic divertion.

Resources
* [Wiki](https://en.wikipedia.org/wiki/DNS_spoofing)

#### I2P
Does not use DNS.

#### TOR
Does not use DNS.

#### Bluetooth
Does not use DNS.

#### WiFi-Direct
Does not use DNS.

#### Full Spectrum Radio
Does not use DNS.

##### LiFi
Does not use DNS.

#### 1M5
Does not use DNS.

### Brute Force

This attack is performed by watching all messages passing between all nodes attempting to correlate messages into paths.

#### I2P

Difficult attack to initiate against I2P as all peers are sending messages frequently across the network (end-to-end and for network maintenance)
and each end-to-end message changes in size and data along its path. The attacker also does not have access to the messages
as inter-router communications are encrypted and in mutiple 'cloves' (garlic) so that two messages each 1kb in size would
appear as one 2kb message.

A bruce force attacker can induce trends by sending an unusually large payload to an I2P destination while monitoring all
network connections eliminating all nodes that didn't receive it. The cost to mount this kind of brute force attack on
I2P would be very expensive so not a high priority. (Could be mitigated in a future release by limiting payload size within chunking.)

Preventing a single router or group of routers from attempting to route all I2P traffic is ensured due to each router
placing limits on the number of tunnels that can be routed by a single peer.

#### TOR

#### Bluetooth

The very limited range of Bluetooth makes this impossible on a large scale.

#### WiFi-Direct

WiFi-Direct has a very limited range and few frequencies so it's not too difficult to brute force a local WiFi frequency.
Globally it would be practically impossible.

#### Full Spectrum Radio

Attempting to listen to all frequencies globally in the full radio spectrum attempting correlation is impossible due to
limited ranges of lower powered sources, although locally it could be possible yet expensive.
If the radio is kept mobile, this attack would be extremely difficult to pull off even locally.
This network client is expected to use forms of frequency hopping and layered encryption making this even further unlikely
to be possible.

#### LiFi
Brute force of a light fidelity would require direct line-of-sight of all entry and exit points of a network using LiFi.
If this LiFi network is just a local one among a few peers, this would not be difficult unless encryption is used.
1M5 will be using LiFi to connect people globally thus making brute force through this client impossible unless every
LiFi mechanism secretly had a listener on it (added by manufacturer). With the use of layered E2EE (End-to-End Encryption),
the ability to correlate the entire network will still be highly unlikely.

#### 1M5

The 1M5 network is slated to provide random delays across its nodes, extended persistent delays (e.g. up to months),
and bandwidth throttling on streams to help combat this attack at the application layer. In addition, trying to
brute force 1M5 users will be even more difficult as the ManCon lowers in value as multiple networks are used
per request requiring watching all messages across all participating networks trying to correlate them all. End-to-end
enryption is used with every message between peers and these messages are wrapped by each network's encryption mechanism
e.g. I2P, Tor, etc.


### Timing

Timing attacks seek to correlate messages across the network based on their latencies combined with expected behavioral
patterns, e.g. HTTP requests receive responses.

#### I2P

I2P uses unidirectional datagrams so there are no replies although this is not the case with streaming and the guaranteed
delivery mode.

#### TOR

Tor uses bidirectional channels so this is more likely successful than I2P and a common attack against specific Tor users.

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5
Random delays can be added to any network relay providing a mitigation to any network used with 1M5.

For example, 1M5 helps Tor by supporting random delays between 1M5 nodes so that if a user must use Tor and feels under threat, multiple
Tor circuits can be stringed together with random delays between them to make this practically impossible.

##### Web Sites

Typical Tor Timing Attack viewing a website:
       |Time Start------------------Time End| = 500ms
Alice -> Tor Guard -> Tor Relay -> Tor Exit -> https://duckduckgo.com/?q=tiananmen+square
Alice <- Tor Guard <- Tor Relay <- Tor Exit <- HTML
       |Time End------------------Time Start| = 500ms

1M5 TT Solution when viewing a website:
```
                        |Time Start-----------------------Random Delay (145ms)--------------------------------------------------Time End| = 1145ms
Alice [1M5 Tor Client] -> Tor Guard -> Tor Relay -> Bob[1M5 Tor Hidden Service to 1M5 Tor Client] -> Tor Guard -> Tor Relay -> Tor Exit -> https://duckduckgo.com/?q=tiananmen+square
Alice [1M5 Tor Client] <- Tor Guard <- Tor Relay <- Bob[1M5 Tor Hidden Service from 1M5 Tor Client] <- Tor Guard <- Tor Relay <- Tor Exit <- HTML
                        |Time End-------------------------Random Delay (55ms)----------------------------------------------------Time Start| = 1055ms
```

1M5 IT Solution when viewing a website:

* Alice lives in repressive jurisdiction, Tor is blocked, and/or feels they're being targeted
* Bob does not and is not likely to be targeted for a timing attack
```
                               |Time Start A1---------------------------------------Random Delay (80ms)------------------------------------------------Time End A1| = 1080ms
                                                                                                                            |Time Start B1-------------Time End B1| = 500ms
Alice [1M5 I2P Client (OBGW)] -> I2P OBP -> I2P OBEP -> I2P IBGW -> I2P IBP -> Bob[1M5 I2P Client (IBEP) to 1M5 Tor Client] -> Tor Guard -> Tor Relay -> Tor Exit -> https://duckduckgo.com/?q=tiananmen+square
Alice [1M5 I2p Client (IBEP)] <- I2P IBP <- I2P IBGW <- I2P OBEP <- I2P OBP <- Bob[1M5 I2P Client (OBGW) from 1M5 Tor Client] <- Tor Guard <- Tor Relay <- Tor Exit <- HTML
                                                                                                                               |Time End B2-------------Time Start B2| = 500ms
                               |Time End A2-----------------------------------------Random Delay (120ms)------------------------------------------------Time Start A2| = 1120ms
```

1M5 B(N)T Solution uses Bluetooth chains to get out to a node that has Tor access using it to fulfill the request returning the result back to the origination.
This pushes timing attack out to the fulfilling Tor Client node which can also be mitigated by chaining Tor circuits for maximum security.

Satellites, Full-Spectrum Radio, and LiFi can be used prior to Tor to get the same result.

##### Peer-to-Peer



### Intersection

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Tagging

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Partitioning

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Predecessor

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Harvesting

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Traffic Analysis Identification

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Sybil

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Buddy Exhaustion

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Cryptographic

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Floodfill Anonymity

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Central Resource

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Development

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Implementation (Bugs)

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Blocklists

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Distributed Denial of Service (DDoS)

A network-attack in which the perpetrator seeks to make a machine or network resource unavailable to its intended users
by temporarily or indefinitely disrupting services of a networked host.

Resources
* [Wiki](https://en.wikipedia.org/wiki/Denial-of-service_attack)

#### Greedy User

##### I2P

##### TOR

##### Bluetooth

##### WiFi-Direct

##### Full Spectrum Radio

##### LiFi

##### 1M5


#### Starvation

##### I2P

##### TOR

##### Bluetooth

##### WiFi-Direct

##### Full Spectrum Radio

##### LiFi

##### 1M5


#### Flooding

##### I2P

##### TOR

##### Bluetooth

##### WiFi-Direct

##### Full Spectrum Radio

##### LiFi

##### 1M5


#### Ping Flood

##### I2P

##### TOR

##### Bluetooth

##### WiFi-Direct

##### Full Spectrum Radio

##### LiFi

##### 1M5


#### CPU Loading

##### I2P

##### TOR

##### Bluetooth

##### WiFi-Direct

##### Full Spectrum Radio

##### LiFi

##### 1M5


#### Floodfill

##### I2P

##### TOR

##### Bluetooth

##### WiFi-Direct

##### Full Spectrum Radio

##### LiFi

##### 1M5


#### ReDoS

##### I2P

##### TOR

##### Bluetooth

##### WiFi-Direct

##### Full Spectrum Radio

##### LiFi

##### 1M5


#### Twinge

##### I2P

##### TOR

##### Bluetooth

##### WiFi-Direct

##### Full Spectrum Radio

##### LiFi

##### 1M5


#### SYN Flood

DDos attacks by initiating TCP/IP handshakes but either not responding with a final ACK or responding with a different IP address.

Resources
* [Wiki](https://en.wikipedia.org/wiki/SYN_flood)

##### I2P

##### TOR

##### Bluetooth

##### WiFi-Direct

##### Full Spectrum Radio

##### LiFi

##### 1M5



#### Layer 7

DDoS attacks on application-layer processes.

Resources

##### I2P

##### TOR

##### Bluetooth

##### WiFi-Direct

##### Full Spectrum Radio

##### LiFi

##### 1M5



#### Ping of Death

##### I2P

##### TOR

##### Bluetooth

##### WiFi-Direct

##### Full Spectrum Radio

##### LiFi

##### 1M5


#### Smurf Attack

Resources
* [Wiki](https://en.wikipedia.org/wiki/Smurf_attack)

##### I2P

##### TOR

##### Bluetooth

##### WiFi-Direct

##### Full Spectrum Radio

##### LiFi

##### 1M5


#### Fraggle Attack

Resources
* [Wiki](https://en.wikipedia.org/wiki/Smurf_attack#Fraggle_Attack)

##### I2P

##### TOR

##### Bluetooth

##### WiFi-Direct

##### Full Spectrum Radio

##### LiFi

##### 1M5


### Advanced Persistent Threat (APT)

A stealthy computer network attack in which a person or group gains unauthorized access to a network and remains
undetected for an extended period.

Resources
* [Wiki](https://en.wikipedia.org/wiki/Advanced_persistent_threat)

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


### Advanced Volatile Threat (AVT)

A stealthy computer network attack in which a person or group gains unauthorized access to a network and remains
undetected in memory never persisting to the hard-drive circumventing investigative techniques.

Resources
* [Wiki](https://en.wikipedia.org/wiki/Advanced_volatile_threat)

#### I2P

#### TOR

#### Bluetooth

#### WiFi-Direct

#### Full Spectrum Radio

#### LiFi

#### 1M5


