Viessmann API Binding
=====================

This binding integrates Viessmann heating devices using the [Viessmann API](https://developer.viessmann.com/start.html).
It provides information similar to what you can get through the ViCare mobile app.

Please note this binding is unofficial and not endorsed by Viessmann in any way.

Requirements
------------

This binding requires OpenHAB 3.4

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

### 3.4.0

* Add official support for OpenHAB 3.4