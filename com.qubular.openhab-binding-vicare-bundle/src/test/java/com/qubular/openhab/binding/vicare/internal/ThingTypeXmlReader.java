package com.qubular.openhab.binding.vicare.internal;

import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.thing.type.ChannelTypeBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.HashMap;
import java.util.Map;

import static javax.xml.stream.XMLStreamConstants.*;

public class ThingTypeXmlReader {
    private Map<ChannelTypeUID, ChannelType> channelTypes = new HashMap<>();

    public void readChannelTypes(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            int eventType = reader.next();
            switch (eventType) {
                case START_ELEMENT:
                    if ("thing-descriptions".equals(reader.getName().getLocalPart())) {
                        readThingDescriptions(reader);
                    }
                    break;
                case XMLStreamConstants.START_DOCUMENT:
                case XMLStreamConstants.END_DOCUMENT:
                case XMLStreamConstants.DTD:
                    // ignore
            }
        }
    }

    private void readThingDescriptions(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            switch (reader.next()) {
                case START_ELEMENT:
                    switch (reader.getName().getLocalPart()) {
                        case "thing-type":
                            break;
                        case "channel-type":
                            readChannelType(reader);
                            break;
                    }
                case END_ELEMENT:
                    if ("thing-descriptions".equals(reader.getName().getLocalPart())) {
                        return;
                    }
            }
        }
    }

    private void readChannelType(XMLStreamReader reader) throws XMLStreamException {
        String channelId = null;
        for (int i = 0; i < reader.getAttributeCount(); ++i) {
            switch (reader.getAttributeLocalName(i)) {
                case "id":
                    channelId = reader.getAttributeValue(i);
                    break;
            }
        }
        if (channelId == null) {
            throw new XMLStreamException("Channel type must have id");
        }
        String itemType = null;
        String label = null;
        while (reader.hasNext()) {
            switch (reader.next()) {
                case START_ELEMENT:
                    switch (reader.getLocalName()) {
                        case "item-type":
                            itemType = reader.getElementText();
                            break;
                        case "label":
                            label = reader.getElementText();
                            break;
                    }
                    break;
                case END_ELEMENT:
                    if ("channel-type".equals(reader.getLocalName())) {
                        ChannelTypeUID channelTypeUID = new ChannelTypeUID(VicareConstants.BINDING_ID, channelId);
                        if (itemType == null) {
                            throw new XMLStreamException(String.format("Channel Type %s does not have an item-type", channelId));
                        }
                        ChannelType replaced = channelTypes.put(channelTypeUID,
                                                           ChannelTypeBuilder.state(channelTypeUID, label, itemType)
                                                                   .build());
                        if (replaced != null) {
                            throw new XMLStreamException(String.format("Duplicate channel-type name %s", channelId));
                        }
                        return;
                    }
                    break;
            }
        }
    }

    public Map<ChannelTypeUID, ChannelType> getChannelTypes() {
        return channelTypes;
    }
}
