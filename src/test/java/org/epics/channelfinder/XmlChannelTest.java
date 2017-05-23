package org.epics.channelfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Test;

public class XmlChannelTest {

    /**
     * Check the comparison of XmlChannel objects.
     */
    @Test
    public void checkEquality() {
        XmlChannel ch1 = new XmlChannel("ch1", "owner1");
        XmlChannel ch2 = new XmlChannel("ch1", "owner2");
        assertThat("The comparison based on channel name failed", ch1.equals(ch2));

        // Check equality works with properties 
        ch1.setProperties(
                new HashSet<>(Arrays.asList(new XmlProperty("p1", "o1", "123"), new XmlProperty("p2", "o1", "abc"))));
        assertFalse("The comparison of channels failed to match properties", ch1.equals(ch2));

        // Check equality based on property set - equality check should be order dependent
        ch2.setProperties(
                new HashSet<>(Arrays.asList(new XmlProperty("p2", "o2", "abc"), new XmlProperty("p1", "o2", "123"))));
        assertThat("The comparison of channels failed to match properties", ch1.equals(ch2));

        // Check equality based on property set - should include property value  
        ch2.setProperties(
                new HashSet<>(Arrays.asList(new XmlProperty("p2", "o", "abcd"), new XmlProperty("p1", "o", "123"))));
        assertFalse(
                "The comparison of channels failed to match properties - should fail due to dissimilar property values",
                ch1.equals(ch2));

        // Check equality based on tag set
        ch1.setTags(new HashSet<>(Arrays.asList(new XmlTag("t1", "o1"), new XmlTag("t2", "o1"))));
        assertFalse("The comparison of channels failed to match tags", ch1.equals(ch2));

        // Check equality based on tag set- equality check should be order dependent
        ch2.setProperties(
                new HashSet<>(Arrays.asList(new XmlProperty("p2", "o2", "abc"), new XmlProperty("p1", "o2", "123"))));
        ch2.setTags(new HashSet<>(Arrays.asList(new XmlTag("t2", "o2"), new XmlTag("t1", "o2"))));
        assertThat("The comparison of channels failed to match tags - equality test should be order agnostics ",
                ch1.equals(ch2));

        
    }

    /**
     * Check the parsing of the XmlChannel to string primarily for logging.
     */
    @Test
    public void checkToLog() {
        XmlChannel ch1 = new XmlChannel("ch1", "owner1");
        String expectedLog = "ch1(owner1):[]";
        assertThat("Failed to create the expected log message: expected " + expectedLog + " received: "
                + XmlChannel.toLog(ch1), expectedLog.equals(XmlChannel.toLog(ch1)));

        ch1.setProperties(
                new HashSet<>(Arrays.asList(new XmlProperty("p1", "o", "123"), new XmlProperty("p2", "o", "abc"))));
        expectedLog = "ch1(owner1):[p1(o):123,p2(o):abc]";
        assertThat("Failed to create the expected log message: expected " + expectedLog + " received: "
                + XmlChannel.toLog(ch1), expectedLog.equals(XmlChannel.toLog(ch1)));

        ch1.setTags(new HashSet<>(Arrays.asList(new XmlTag("t1", "o"), new XmlTag("t2", "o"))));
        expectedLog = "ch1(owner1):[p1(o):123,p2(o):abct1(o),t2(o)]";
        assertThat("Failed to create the expected log message: expected " + expectedLog + " received: "
                + XmlChannel.toLog(ch1), expectedLog.equals(XmlChannel.toLog(ch1)));
    }
}
