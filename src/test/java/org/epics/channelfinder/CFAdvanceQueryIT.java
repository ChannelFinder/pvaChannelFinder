package org.epics.channelfinder;

import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.epics.nt.NTURI;
import org.epics.nt.NTURIBuilder;
import org.epics.pvaccess.client.rpc.RPCClientImpl;
import org.epics.pvdata.pv.PVStructure;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Integration tests for filtering, sorting, and pagination
 * 
 * @author Kunal Shroff
 *
 */
public class CFAdvanceQueryIT {

    private static RPCClientImpl client;

    @BeforeClass
    public static void createDB() {
        client = new RPCClientImpl(ChannelFinderService.SERVICE_NAME);
    }

    @Test
    public void filterTest() {

        List<XmlChannel> resultChannels;

        NTURIBuilder uriBuilder = NTURI.createBuilder()
                .addQueryString("_name")
                .addQueryString("_size")
                .addQueryString("_filter");
        NTURI uri = uriBuilder.create();
        uri.getPVStructure().getStringField("scheme").put("pva");
        uri.getPVStructure().getStringField("path").put("channels");
        uri.getQuery().getStringField("_name").put("*");
        uri.getQuery().getStringField("_size").put("10");

        try {
            // When the _filter is used with ALL then you only get channelNames 
            uri.getQuery().getStringField("_filter").put("ALL");
            PVStructure result = client.request(uri.getPVStructure(), 3.0);
            resultChannels = XmlUtil.parse(result);
            assertTrue("Failed to filter the result to only return the channel Names ", resultChannels.stream().allMatch((ch) -> {
                return ch.getProperties().isEmpty() && ch.getTags().isEmpty();
            }));
            // Check channels only consist of names
            
            Set<String> filterNames = new HashSet<>();
            filterNames.add("type");
            filterNames.add("cell");
            filterNames.add("family");

            uri.getQuery().getStringField("_filter").put(filterNames.stream().collect(Collectors.joining(",")));
            result = client.request(uri.getPVStructure(), 3.0);
            resultChannels = XmlUtil.parse(result);

            assertTrue("Failed to filter the result to only return the channel names and a selecte few properties" + 
            "expected only the properties type, cell, and family recieved " + result,
                    resultChannels.stream().allMatch((ch) -> {
                return filterNames
                        .equals(ch.getProperties().stream().map(XmlProperty::getName).collect(Collectors.toSet()))
                        && 
                        ch.getTags().isEmpty();
            }));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void sortTest() {

    }

    @Test
    public void sizeTest() {

        NTURIBuilder uriBuilder = NTURI.createBuilder().addQueryString("_name").addQueryString("_size")
                .addQueryString("_from");
        NTURI uri = uriBuilder.create();

        uri.getPVStructure().getStringField("scheme").put("pva");
        uri.getPVStructure().getStringField("path").put("channels");
        uri.getQuery().getStringField("_name").put("*");
        uri.getQuery().getStringField("_size").put("10");
        uri.getQuery().getStringField("_from").put("0");
        try {
            PVStructure result = client.request(uri.getPVStructure(), 3.0);
            List<XmlChannel> channels = XmlUtil.parse(result);
            assertTrue("Failed to limit the search result using the _size argument, expected: 10 found: "
                    + channels.size(), channels.size() == 10);

            uri.getQuery().getStringField("_size").put("143");
            result = client.request(uri.getPVStructure(), 3.0);
            channels = XmlUtil.parse(result);
            assertTrue("Failed to limit the search result using the _size argument, expected: 143 found: "
                    + channels.size(), channels.size() == 143);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void paginationTest() {
        NTURIBuilder uriBuilder = NTURI.createBuilder().addQueryString("_name").addQueryString("_size")
                .addQueryString("_from");
        NTURI uri = uriBuilder.create();

        uri.getPVStructure().getStringField("scheme").put("pva");
        uri.getPVStructure().getStringField("path").put("channels");
        uri.getQuery().getStringField("_name").put("*");

        try {

            uri.getQuery().getStringField("_size").put("10");
            uri.getQuery().getStringField("_from").put("0");
            Set<XmlChannel> results = new HashSet<>();
            results.addAll(XmlUtil.parse(client.request(uri.getPVStructure(), 3.0)));

            uri.getQuery().getStringField("_from").put("2");
            results.addAll(XmlUtil.parse(client.request(uri.getPVStructure(), 3.0)));
            assertTrue("Pagination Test: _size & _from when used should result in a ordered set of channels"
                    + results.size(), results.size() == 12);

            uri.getQuery().getStringField("_from").put("5");
            results.addAll(XmlUtil.parse(client.request(uri.getPVStructure(), 3.0)));
            assertTrue("Pagination Test: _size & _from when used should result in a ordered set of channels"
                    + results.size(), results.size() == 15);

            uri.getQuery().getStringField("_from").put("10");
            results.addAll(XmlUtil.parse(client.request(uri.getPVStructure(), 3.0)));
            assertTrue("Pagination Test: _size & _from when used should result in a ordered set of channels"
                    + results.size(), results.size() == 20);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    /**
     * 
     */
    @AfterClass
    public static void cleanupDB() {
        client.destroy();
    }
}
