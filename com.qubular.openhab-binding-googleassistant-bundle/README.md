# Google Assistant Binding

This binding enables the sending of commands to Google Assistant to control items from OpenHAB that are supported by Google Assistant but do not have any native OpenHAB binding.
## Supported Things

Only one thing named `googleassistant` is available.
It can be extended with different channels.

## Binding Configuration

The binding has some global configuration. In order to use this binding, you will need to sign up for a Google Developer 
account and enable the Google Assistant API. You will also need to configure OAuth2 client credentials for your Google account.

Once this is done, you will need to [configure the binding](/settings/addons/googleassistant/config) with the client credentials by pasting the credential JSON into the 
OpenHAB binding. Then once this is done, visit the [authorisation page](/googleAssistant/doAuthorisation) to allow OpenHAB access to your Google Account.

After doing this, you will need to create a Speaker device in the Google Assistant API; this can be done
by visiting the [device registration page](/googleAssistant/deviceRegistration)

## Thing Configuration

TODO describe Thing configuration here

## Channels

TODO describe the available channels here