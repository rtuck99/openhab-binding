Viessmann API Binding
=====================

This binding integrates Viessmann heating devices using the [Viessmann API](https://developer.viessmann.com/start.html).
It provides information similar to what you can get through the ViCare mobile app.

Please note this binding is unofficial and not endorsed by Viessmann in any way.

This binding has been developed against 3.3.0 stable release and is not 
supported for 3.4.0 milestone builds.

Requirements
------------

This binding requires OpenHAB 3.3

Supported Devices
----------------

Currently this binding has only been tested with Vitodens 100-W in a fairly
basic configuration, however it should support other Viessmann devices.

Supported Features
------------------

Below is an incomplete summary of the main features supported - depending on your boiler model and/or heating
configuration some or all of these may or may not be present:

* Read and write of active heating circuit operating mode.
* Temperature sensors
* Temperature setpoints (read/write)
* Supply temperature max/min limit (read/write)
* Burner status
* Burner modulation
* Burner statistics (hours and starts)
* Circulation pump status
* Frost protection status
* Basic text properties: device serial number etc.
* DHW, Heating and Total consumption statistics
* Program mode temperature settings
* DHW Heating status
* DHW Hot water storage temperature
* DHW Primary and Circulation Pump Status
* DHW target temperature (read/write)
* DHW One time charge mode (read/write)
* Holiday program settings (read-only)
* Holiday-at-home program settings (read-only)
* Heating curve settings
* Heating circuit names
* Heating circuit operating mode (read/write)
* Heating circuit supply temperature
* Extended heating mode
* Solar production statistics
* Solar collector temperature
* Solar circuit pump status
* Heat pump compressor status
* Heat pump compressor statistics
* Heat pump primary and secondary supply temperature sensors

Configuring
-----------

In order to use the binding, you will need to have a Viessmann API account and
configure it with the redirect URL for your OpenHAB installation and then authorise 
the binding. Follow the instructions in openhab at `http://<Your OpenHAB>/vicare/setup`

Create an instance of the Viessmann API Bridge thing, and configure it with your Client ID 
which you should obtain from the Viessmann Developer portal. The developer portal should be 
configured with the redirect URI shown on the setup page. Then you should be able to 
authorise the Viessmann binding by clicking on the Authorise button on the setup page.

After authorising the binding, then it should automatically discover any heating devices you have
and they will appear in your Inbox.


Changelog
---------

### 3.3.6

* Fix #29 VicareDiscoveryService fails if some properties are null
* Fix #25 README + setup instructions are misleading
* Fix #24 Support for heat pump features
* Fix #23 Create equipment from Thing fails with "Bad Request"
* Fix #21 Support setting for DHW target temperature heating.dhw.temperature.main
* Fix #9 Add response capture for installations and gateways
* Support additional features. 
  Note: Some of the channel names with string values may have changed slightly -
  heating_circuits_operating_modes_active, device_serial may now have _value suffixes.
  Please check your Items for missing channel links and relink if upgrading from a previous version.
* Support for dynamic channel types - channel labels should now clearly identify which heating 
  circuit they are associated with, and max/min values should be exposed for controllable values.


#### 3.3.5

* Fix #19 Using openhab in Docker prevents authentication - Illegal key size - CRYPTO_POLICY ==> limited
* Fix #18 Binding makes excessive calls during power outage
* Fix #17 Text features missing from boiler channels
* Additional device properties available on Thing 

#### 3.3.4

* Add read/write support for heating.circuits.N.temperature.levels
* Add write support for operating program temperature setpoints
* Fix #16 Cannot start binding from new install - token store not initialized

#### 3.3.3

* Stored tokens are now encrypted
* Support for read and write of heating.circuits.N.operating.modes.active
* Channels are now sorted
* Fix #5 The binding should automatically discover devices after OAuth access token is granted the first time.
* Fix #7 If binding throws unhandled exception, things are no longer updated

#### 3.3.2 
Polling interval is now configurable.

Read-only support for additional features:
* Burner status
* DHW Heating status
* DHW Hot water storage temperature
* DHW Primary and Circulation Pump Status
* Holiday Program settings
* Heating curve settings

Note that the names of some channels may have changed slightly in order to
distinguish between "active" and "status". Status channels are now of String type
and not Switch - you may need to either change your linked item type accordingly or
apply a transformation to the value. 

Fix #2 If more than one boiler is configured, the binding doesn't work correctly

#### 3.3.1
This is the initial release
