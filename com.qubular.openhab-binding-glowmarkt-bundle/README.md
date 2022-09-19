Glowmarkt API Binding
=====================

This binding allows import of Smart Meter data from the Hildebrand's [Glowmarkt API](https://glowmarkt.com/).
The information here is similar to what can be obtained using their Bright app. 
You can use this to get your historic meter data from DCC. 

Configuring
-----------

In order to use this binding, you will need to register for an account to use
the Glowmarkt API, and agree for them to import your Smart Meter data from DCC.

You will also need to install and configure an OpenHAB persistence service
that supports updating of past Item data via `ModifiablePersistenceService`.
As of OpenHAB 3.3.0 the default RRD4J persistence service doesn't support this,
so you will need to use one of the other persistence services such as JDBC.

Once you have added the Glowmarkt API Bridge Thing, configure the User name and 
password for your Glowmarkt API access, and the name of your persistence service.

It should then discover the smart meter Virtual Entity Thing and it will appear 
in your Inbox. The Virtual Entity should then have the gas and electricity channels. 

It will then attempt to download the gas and electricity meter readings. Be aware this 
may take some time to complete.

Supported Channels
------------------

* Gas Consumption
* Gas Cost
* Electricity Consumption
* Electricity Cost

The downloaded data is at 30-minute interval resolution, and it will download all the data 
that it can find. Every 24 hours it will download any new data it doesn't already have.

Changelog
---------

* 3.3.1 This is the initial release.
