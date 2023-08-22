Viessmann API Binding
=====================

This binding integrates Viessmann heating devices using the [Viessmann API](https://developer.viessmann.com/start.html).
It provides information similar to what you can get through the ViCare mobile app.

Please note this binding is unofficial and not endorsed by Viessmann in any way.

Requirements
------------

This binding requires OpenHAB 4.0. For earlier versions of OpenHAB please refer to previous versions of this binding on
the community marketplace discussion for the corresponding binding version. 

Upgrading from OpenHAB 3.4
--------------------------

It is advisable to remove previous versions of the plugin before upgrading OpenHAB. 

However if you have upgraded to OpenHAB 4.0 and still have the old binding installed, remove it from the
Settings->Bindings admin page. This may fail, in which case you will need to log into the OpenHAB shell
and run

    feature:list | grep qub

If the feature is still installed, remove it with

    feature:uninstall com.qubular.openhab-binding-vicare-feature

However if removal of the feature did not succeed, the bundles may also need to be removed. If the following

    bundle:list | grep qub

shows that the bundles are still installed, then remove them as follows

    bundle:uninstall com.qubular.openhab-binding-vicare-bundle
    bundle:uninstall com.qubular.vicare-osgi

Once the old binding is fully removed, you can install the new version from the Settings->Bindings page

There is no need to add or remove any of the Things they will still work with the new binding.

Supported Devices
----------------

This binding has only been developed against a Vitodens 100-W in a fairly
basic configuration, however it should be able to work against a range of other Viessmann heating and ventilation 
devices including heat pumps and solar heating, although not all features on these may be supported yet.

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
* DHW Charging active
* DHW Hot water storage temperature
* DHW Primary and Circulation Pump Status
* DHW target temperature (read/write)
* DHW One time charge mode (read/write)
* DHW Temperature Hysteresis (read/write)
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
* Ventilation operating mode and operating program
* Ventilation holiday program (read-only)
* Heating buffer temperature sensors

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

### 4.0.3

* Fix for #57 Rework prefetching

### 4.0.2

* Fixed #55 Karaf refreshes binding on restart and unloads it
* Fix for #57 Threading issues and log spam after HTTP connection EOF
* Fix for #56 support for heating.buffer.sensors.temperature.main temperature value

### 4.0.1

* Fixed #48 Slow startup of Viessmann binding under 4.0.0
* Fixed #50 Viessmann bridge can't be added
* Fixed #54 Feature endpoint now returns incorrect payload

### 4.0.0

This version introduces support for OpenHAB 4.0
