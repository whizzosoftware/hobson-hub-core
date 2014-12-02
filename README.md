# Hobson

**Hobson** is a cross-platform automation hub and aggregator for the Internet of Things.

* It provides a common way to control smart devices such as lights, cameras and thermostats from a variety of vendors
* It has no dependency on paid services so can be run for free
* It is designed to be lightweight and can run on resource-limited devices
* It is highly modular and designed to be extended via a well-documented API 

To find out more and download ready-to-run installers, please visit the 
[Hobson website](http://www.hobson-automation.com).

## hobson-hub-core

The core plugin provides foundational services used by the rest of the Hub.
These include:

* A logging facility
* Hub network advertisement via ZeroConf (JmDNS)
* Manager classes used by all Hobson plugins
* A RESTful API for Hub control and management