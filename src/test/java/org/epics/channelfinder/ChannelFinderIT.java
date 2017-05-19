package org.epics.channelfinder;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.epics.channelfinder.ChannelFinderService.channelMapper;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.epics.nt.NTURI;
import org.epics.nt.NTURIBuilder;
import org.epics.pvaccess.client.rpc.RPCClientImpl;
import org.epics.pvdata.pv.PVStructure;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class ChannelFinderIT {

    private static final Logger log = Logger.getLogger(ChannelFinderIT.class.getCanonicalName());
    private static final XmlChannel ch1 = new XmlChannel("pvk:01<first>", "channel",
            new HashSet<>(Arrays.asList(new XmlProperty("IT_prop", "owner", "1"), new XmlProperty("IT_prop2", "owner", "2"))),
            new HashSet<>(Arrays.asList(new XmlTag("IT_Taga"))));
    private static final XmlChannel ch2 = new XmlChannel("pvk:02<second>", "channel",
            new HashSet<>(Arrays.asList(new XmlProperty("IT_prop", "owner", "1"))),
            new HashSet<>(Arrays.asList(new XmlTag("IT_Taga", "owner"), new XmlTag("IT_Tagb", "owner"))));
    private static final XmlChannel ch3 = new XmlChannel("pvk:03<second>", "channel",
            new HashSet<>(Arrays.asList(new XmlProperty("IT_prop", "owner", "2"))),
            new HashSet<>(Arrays.asList(new XmlTag("IT_Tagb", "owner"), new XmlTag("IT_Tagc", "owner"))));
    private static final XmlChannel ch4 = new XmlChannel("distinctName", "channel",
            new HashSet<>(Arrays.asList(new XmlProperty("IT_prop", "owner", "*"))), 
            new HashSet<>(Arrays.asList(new XmlTag("IT_Tag*", "owner"))));
    private static final List<XmlChannel> testChannels = Arrays.asList(ch1, ch2, ch3, ch4);

    /**
     * Setup the channels to be used to check the query functionality of
     * ChannelFinder
     */
    @BeforeClass
    public static void setup() {
        TransportClient client = ElasticSearchClientManager.getClient();

        try {
            BulkRequestBuilder bulkRequest = client.prepareBulk();

            bulkRequest.add(new IndexRequest("tags", "tag", "IT_Taga")
                    .source(jsonBuilder().startObject().field("name", "IT_Taga").field("owner", "owner").endObject()));
            bulkRequest.add(new IndexRequest("tags", "tag", "IT_Tagb")
                    .source(jsonBuilder().startObject().field("name", "IT_Tagb").field("owner", "owner").endObject()));
            bulkRequest.add(new IndexRequest("tags", "tag", "IT_Tagc")
                    .source(jsonBuilder().startObject().field("name", "IT_Tagc").field("owner", "owner").endObject()));
            bulkRequest.add(new IndexRequest("tags", "tag", "IT_Tag*")
                    .source(jsonBuilder().startObject().field("name", "IT_Tag*").field("owner", "owner").endObject()));

            bulkRequest.add(new IndexRequest("properties", "property", "IT_prop")
                    .source(jsonBuilder().startObject().field("name", "IT_prop").field("owner", "owner").endObject()));
            bulkRequest.add(new IndexRequest("properties", "property", "IT_prop2")
                    .source(jsonBuilder().startObject().field("name", "IT_prop2").field("owner", "owner").endObject()));

            for (XmlChannel channel : testChannels) {
                bulkRequest.add(client.prepareUpdate("channelfinder", "channel", channel.getName())
                        .setDoc(channelMapper.writeValueAsBytes(channel))
                        .setUpsert(new IndexRequest("channelfinder", "channel", channel.getName())
                                .source(channelMapper.writeValueAsBytes(channel))));
            }
            bulkRequest.setRefresh(true);
            BulkResponse bulkResponse = bulkRequest.execute().actionGet();
            if (bulkResponse.hasFailures()) {
                log.log(Level.SEVERE, bulkResponse.buildFailureMessage(), bulkResponse);
                fail(bulkResponse.buildFailureMessage());
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
            fail(e.getMessage());
        }

    }

    private RPCClientImpl client = new RPCClientImpl("ChannelFinderService");

    @Test
    public void queryByName1() {
        NTURIBuilder uriBuilder = NTURI.createBuilder();
        uriBuilder.addQueryString("_name");
        NTURI uri = uriBuilder.create();
        uri.getPVStructure().getStringField("scheme").put("pva");
        uri.getPVStructure().getStringField("path").put("channels");
        uri.getQuery().getStringField("_name").put("pvk:0?<*");

        try {
            PVStructure result = client.request(uri.getPVStructure(), 3.0);
            List<XmlChannel> channels = XmlUtil.parse(result);
            assertTrue("Failed to query by name, expected: 3 found: "+channels.size(), channels.size() == 3);
            assertTrue("Failed to find channel " + ch1.getName(), channels.contains(ch1));
            assertTrue("Failed to find channel " + ch2.getName(), channels.contains(ch2));
            assertTrue("Failed to find channel " + ch3.getName(), channels.contains(ch3));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void queryByName2() {
        NTURIBuilder uriBuilder = NTURI.createBuilder();
        uriBuilder.addQueryString("_name");
        NTURI uri = uriBuilder.create();
        uri.getPVStructure().getStringField("scheme").put("pva");
        uri.getPVStructure().getStringField("path").put("channels");
        uri.getQuery().getStringField("_name").put("pvk:01<first>|pvk:02<second>");

        try {
            PVStructure result = client.request(uri.getPVStructure(), 3.0);
            List<XmlChannel> channels = XmlUtil.parse(result);
            assertTrue("Failed to find channels on '|' seperated name pattern, expected: 2 found: " + channels.size(),
                    channels.size() == 2);
            assertTrue("Failed to find channel " + ch1.getName(), channels.contains(ch1));
            assertTrue("Failed to find channel " + ch2.getName(), channels.contains(ch2));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void queryLimits() {
        NTURIBuilder uriBuilder = NTURI.createBuilder();
        uriBuilder.addQueryString("_name");
        uriBuilder.addQueryString("_size");
        NTURI uri = uriBuilder.create();
        uri.getPVStructure().getStringField("scheme").put("pva");
        uri.getPVStructure().getStringField("path").put("channels");
        uri.getQuery().getStringField("_name").put("*");

        try {
            uri.getQuery().getStringField("_size").put("1");
            PVStructure result = client.request(uri.getPVStructure(), 3.0);
            List<XmlChannel> channels = XmlUtil.parse(result);
            assertTrue("Failed to find channels limited by size, expected: 1 found: " + channels.size(),
                    channels.size() == 1);
            uri.getQuery().getStringField("_size").put("4");
            result = client.request(uri.getPVStructure(), 3.0);
            channels = XmlUtil.parse(result);
            assertTrue("Failed to find channels limited by size, expected: 4 found: " + channels.size(),
                    channels.size() == 4);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void queryByTag1() {
        NTURIBuilder uriBuilder = NTURI.createBuilder();
        uriBuilder.addQueryString("_tag");
        NTURI uri = uriBuilder.create();
        uri.getPVStructure().getStringField("scheme").put("pva");
        uri.getPVStructure().getStringField("path").put("channels");
        uri.getQuery().getStringField("_tag").put("IT_Taga");

        try {
            PVStructure result = client.request(uri.getPVStructure(), 3.0);
            List<XmlChannel> channels = XmlUtil.parse(result);
            assertTrue("Failed to find channels based on tag name, expected: 2 found: " + channels.size(),
                    channels.size() == 2);
            assertTrue("Failed to find channel " + ch1.getName(), channels.contains(ch1));
            assertTrue("Failed to find channel " + ch2.getName(), channels.contains(ch2));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void queryByTag2() {
        NTURIBuilder uriBuilder = NTURI.createBuilder();
        uriBuilder.addQueryString("_tag");
        NTURI uri = uriBuilder.create();
        uri.getPVStructure().getStringField("scheme").put("pva");
        uri.getPVStructure().getStringField("path").put("channels");
        uri.getQuery().getStringField("_tag").put("IT_Taga|IT_Tagb");

        try {
            PVStructure result = client.request(uri.getPVStructure(), 3.0);
            List<XmlChannel> channels = XmlUtil.parse(result);
            assertTrue("Failed to find channels based on tag name, expected: 3 found: " + channels.size(),
                    channels.size() == 3);
            assertTrue("Failed to find channel " + ch1.getName(), channels.contains(ch1));
            assertTrue("Failed to find channel " + ch2.getName(), channels.contains(ch2));
            assertTrue("Failed to find channel " + ch3.getName(), channels.contains(ch3));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void queryByTag3() {
        NTURIBuilder uriBuilder = NTURI.createBuilder();
        uriBuilder.addQueryString("_tag");
        NTURI uri = uriBuilder.create();
        uri.getPVStructure().getStringField("scheme").put("pva");
        uri.getPVStructure().getStringField("path").put("channels");
        uri.getQuery().getStringField("_tag").put("IT_Taga&IT_Tagb");

        try {
            PVStructure result = client.request(uri.getPVStructure(), 3.0);
            List<XmlChannel> channels = XmlUtil.parse(result);
            assertTrue("Failed to find channels based on tag name, expected: 1 found: " + channels.size(),
                    channels.size() == 1);
            assertTrue("Failed to find channel " + ch2.getName(), channels.contains(ch2));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void queryByTag4() {
        NTURIBuilder uriBuilder = NTURI.createBuilder();
        uriBuilder.addQueryString("_tag");
        NTURI uri = uriBuilder.create();
        uri.getPVStructure().getStringField("scheme").put("pva");
        uri.getPVStructure().getStringField("path").put("channels");

        try {
            uri.getQuery().getStringField("_tag").put("IT_Tag*");
            PVStructure result = client.request(uri.getPVStructure(), 3.0);
            List<XmlChannel> channels = XmlUtil.parse(result);
            assertTrue("Failed to find channels based on tag name, expected: 1 found: " + channels.size(),
                    channels.size() == 1);
            assertTrue("Failed to find channel " + ch4.getName(), channels.contains(ch4));

            uri.getQuery().getStringField("_tag").put("IT_Tag\\*");
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void queryByProperty1() {
        NTURIBuilder uriBuilder = NTURI.createBuilder();
        uriBuilder.addQueryString("IT_prop");
        NTURI uri = uriBuilder.create();
        uri.getPVStructure().getStringField("scheme").put("pva");
        uri.getPVStructure().getStringField("path").put("channels");
        uri.getQuery().getStringField("IT_prop").put("2");

        try {
            PVStructure result = client.request(uri.getPVStructure(), 3.0);
            List<XmlChannel> channels = XmlUtil.parse(result);
            assertTrue("Failed to find channels based on tag name, expected: 1 found: " + channels.size(),
                    channels.size() == 1);
            assertTrue("Failed to find channel " + ch3.getName(), channels.contains(ch3));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void queryByProperty2() {
        NTURIBuilder uriBuilder = NTURI.createBuilder();
        uriBuilder.addQueryString("IT_prop");
        NTURI uri = uriBuilder.create();
        uri.getPVStructure().getStringField("scheme").put("pva");
        uri.getPVStructure().getStringField("path").put("channels");
        uri.getQuery().getStringField("IT_prop").put("1|2");

        try {
            PVStructure result = client.request(uri.getPVStructure(), 3.0);
            List<XmlChannel> channels = XmlUtil.parse(result);
            assertTrue("Failed to find channels based on tag name, expected: 3 found: " + channels.size(),
                    channels.size() == 3);
            assertTrue("Failed to find channel " + ch1.getName(), channels.contains(ch1));
            assertTrue("Failed to find channel " + ch2.getName(), channels.contains(ch2));
            assertTrue("Failed to find channel " + ch3.getName(), channels.contains(ch3));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void queryByProperty3() {
        NTURIBuilder uriBuilder = NTURI.createBuilder();
        uriBuilder.addQueryString("IT_prop");
        uriBuilder.addQueryString("IT_prop2");
        NTURI uri = uriBuilder.create();
        uri.getPVStructure().getStringField("scheme").put("pva");
        uri.getPVStructure().getStringField("path").put("channels");
        uri.getQuery().getStringField("IT_prop").put("1");
        uri.getQuery().getStringField("IT_prop2").put("2");

        try {
            PVStructure result = client.request(uri.getPVStructure(), 3.0);
            List<XmlChannel> channels = XmlUtil.parse(result);
            assertTrue("Failed to find channels based on tag name, expected: 1 found: " + channels.size(),
                    channels.size() == 1);
            assertTrue("Failed to find channel " + ch1.getName(), channels.contains(ch1));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void cleanup() {
        TransportClient client = ElasticSearchClientManager.getClient();

        client.delete(new DeleteRequest("tags", "tag", "IT_Taga")).actionGet();
        client.delete(new DeleteRequest("tags", "tag", "IT_Tagb")).actionGet();
        client.delete(new DeleteRequest("tags", "tag", "IT_Tagc")).actionGet();
        client.delete(new DeleteRequest("tags", "tag", "IT_Tag*")).actionGet();

        client.delete(new DeleteRequest("properties", "property", "IT_prop")).actionGet();
        client.delete(new DeleteRequest("properties", "property", "IT_prop2")).actionGet();

        for (XmlChannel xmlChannel : testChannels) {
            client.delete(new DeleteRequest("channelfinder", "channel", xmlChannel.getName())).actionGet();
        }
    }
}
