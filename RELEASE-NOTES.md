# Release Notes

## 0.6.6 - In Progress
+ Integrating Bitcoin
+ Implementing DEX
+ Expanding network knowledge through graph for enhanced routing

## 0.6.5 - Released
+ Updated roadmap to reflect new hardware ideas
+ Implementing auto-routing between TOR, I2P, and BT when each is blocked.

## 0.6.4 - Released
+ Added TOR hidden services when TOR is local using its Control API
+ Added TOR peer discovery although not yet tested; will test during Browser version (0.6.6)
+ Added Settings and Ops to Desktop GUI
+ Improved Bluetooth's Device, Service, and Peer Discovery fixing some bugs
+ Added ability to start/stop TOR, I2P, and Bluetooth sensors
+ Abstracted out Network Peer discovery so that all sensors can leverage the code
+ Implemented Network Ops more in TOR, I2P, and Bluetooth sensors; need to test during Browser version
+ Submitted integration proposal with Monero

## 0.6.3 - Released
+ Provide Operation interface - not in wide use yet though
+ Move all code to one project to simplify development and releases
+ Add OpenJFX as GUI
+ Additional information saved with Peer relationships in graph
+ Basic DID support started in GUI
+ General GUI frame created
+ Separate desktop into its own module so that the router module can be ran as a sole daemon for environments without graphics support
+ Added CLI module
+ Added API module for simplifying access to Router deamon by both CLI and Desktop modules
+ Moved Data and Utils into a common module to be shared by all projects
+ Moved all Peer information except id to an embedded Derby database to relieve the graph of excess data
+ Greatly improved sensor/network/mancon routing rules
+ Add Network Ops concept, not yet in full use
+ Submitted integration proposal with Bisq

