package com.qubular.binding.glowmarkt.internal;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.thing.type.ChannelTypeProvider;
import org.openhab.core.thing.type.ChannelTypeUID;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class XmlChannelTypeProvider implements ChannelTypeProvider {
    public final Map<ChannelTypeUID, ChannelType> channelTypes = new HashMap<>();

    XmlChannelTypeProvider() {
        try {
            XMLStreamReader xmlStreamReader = XMLInputFactory.newFactory().createXMLStreamReader(
                    XmlChannelTypeProvider.class.getResourceAsStream("/OH-INF/thing/thing-types.xml"));
            ThingTypeXmlReader thingTypeXmlReader = new ThingTypeXmlReader();
            thingTypeXmlReader.readChannelTypes(xmlStreamReader);
            channelTypes.putAll(thingTypeXmlReader.getChannelTypes());
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Collection<ChannelType> getChannelTypes(@Nullable Locale locale) {
        return channelTypes.values();
    }

    @Override
    public @Nullable ChannelType getChannelType(ChannelTypeUID channelTypeUID, @Nullable Locale locale) {
        return channelTypes.get(channelTypeUID);
    }
}
