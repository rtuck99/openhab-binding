Viessmann API Binding
=====================

This binding integrates Viessmann heating devices using the [Viessmann API](https://developer.viessmann.com/start.html).
It provides information similar to what you can get through the ViCare mobile app.

Please note this binding is unofficial and not endorsed by Viessmann in any way.

Supported Devices
----------------

Currently this binding has only been tested with Vitodens 100-W in a fairly
basic configuration, however it should support other Viessmann devices.

Supported Features
------------------

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
* Consumption statistics
* Program mode temperature settings
* DHW Heating status
* DHW Hot water storage temperature
* DHW Primary and Circulation Pump Status
* Holiday Program settings
* Heating curve settings

Configuring
-----------

In order to use the binding, you will need to have a Viessmann API account and
configure it with the redirect URL for your OpenHAB installation and then authorise 
the binding. Follow the instructions in openhab at `http://<Your OpenHAB>/vicare/setup`

After authorising the binding, you should be able to add the Viessmann API Bridge item.
Configure it with your Client ID which you should obtain from the Viessmann Developer
portal.

Once configured, then it should automatically discover any heating devices you have
and they will appear in your Inbox.


Changelog
---------

#### 3.3.5

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
