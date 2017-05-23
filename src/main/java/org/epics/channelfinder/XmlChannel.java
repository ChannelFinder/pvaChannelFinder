package org.epics.channelfinder;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Channel object that can be represented as XML/JSON in payload data.
 *
 *@author Kunal Shroff, Ralph Lange {@literal <ralph.lange@gmx.de>}
 */

@XmlRootElement(name = "channel")
@XmlType(propOrder = { "name", "owner", "properties", "tags" })
public class XmlChannel {
    private String name;
    private String owner;
    private Set<XmlProperty> properties = new HashSet<XmlProperty>();
    private Set<XmlTag> tags = new HashSet<XmlTag>();

    /** Creates a new instance of XmlChannel */
    public XmlChannel() {
    }

    /**
     * Creates a new instance of XmlChannel.
     *
     * @param name - channel name
     */
    public XmlChannel(String name) {
        this.name = name;
    }

    /**
     * Creates a new instance of XmlChannel.
     *
     * @param name - channel name
     * @param owner - owner name
     */
    public XmlChannel(String name, String owner) {
        this.name = name;
        this.owner = owner;
    }

    /**
     * 
     * @param name - channel name
     * @param owner - channel owner
     * @param properties - list of channel properties
     * @param tags - list of channel tags
     */
    public XmlChannel(String name, String owner, Set<XmlProperty> properties, Set<XmlTag> tags) {
        this.name = name;
        this.owner = owner;
        this.properties = properties;
        this.tags = tags;
    }

    /**
     * Getter for channel name.
     *
     * @return name - channel name
     */
    public String getName() {
        return name;
    }

    /**
     * Setter for channel name.
     *
     * @param name - channel name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Getter for channel owner.
     *
     * @return owner - channel owner
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Setter for channel owner.
     *
     * @param owner - channel owner
     */
    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Set<XmlProperty> getProperties() {
        return properties;
    }

    public void setProperties(Set<XmlProperty> properties) {
        this.properties = properties;
    }

    public Set<XmlTag> getTags() {
        return tags;
    }

    public void setTags(Set<XmlTag> tags) {
        this.tags = tags;
    }

    /**
     * Creates a compact string representation for the log.
     *
     * @param data - XmlChannel to create the string representation for
     * @return string representation
     */
    public static String toLog(XmlChannel data) {
        return data.getName() + "(" + data.getOwner() + "):["
                + (data.properties.stream().map(XmlProperty::toLog).collect(Collectors.joining(",")))
                + (data.tags.stream().map(XmlTag::toLog).collect(Collectors.joining(","))) + "]";
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((properties == null) ? 0 : properties.hashCode());
        result = prime * result + ((tags == null) ? 0 : tags.hashCode());
        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof XmlChannel))
            return false;
        XmlChannel other = (XmlChannel) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (properties == null) {
            if (other.properties != null)
                return false;
        } else if (!properties.equals(other.properties))
            return false;
        if (tags == null) {
            if (other.tags != null)
                return false;
        } else if (!tags.equals(other.tags))
            return false;
        return true;
    }

}
