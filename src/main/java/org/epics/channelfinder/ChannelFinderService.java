package org.epics.channelfinder;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.disMaxQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.DisMaxQueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.epics.nt.NTTable;
import org.epics.nt.NTTableBuilder;
import org.epics.nt.NTURI;
import org.epics.pvaccess.PVAException;
import org.epics.pvaccess.server.rpc.RPCRequestException;
import org.epics.pvaccess.server.rpc.RPCServer;
import org.epics.pvaccess.server.rpc.RPCService;
import org.epics.pvdata.pv.PVBooleanArray;
import org.epics.pvdata.pv.PVString;
import org.epics.pvdata.pv.PVStringArray;
import org.epics.pvdata.pv.PVStructure;
import org.epics.pvdata.pv.ScalarType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 
 * @author Kunal Shroff
 *
 */
public class ChannelFinderService {
    private static Logger log = Logger.getLogger(ChannelFinderService.class.getCanonicalName());
    
    public final static String SERVICE_NAME = "ChannelFinderService";

    public static final ObjectMapper channelMapper = new ObjectMapper()
                                                    .addMixIn(XmlProperty.class, OnlyXmlProperty.class)
                                                    .addMixIn(XmlTag.class, OnlyXmlTag.class);

    private static class ChannelFinderServiceImpl implements RPCService {
        private static Logger log = Logger.getLogger(ChannelFinderServiceImpl.class.getCanonicalName());

        private static final ChannelFinderServiceImpl instance = new ChannelFinderServiceImpl();


        private ChannelFinderServiceImpl() {
            log.info("start");
        }

        public static ChannelFinderServiceImpl getInstance() {
            return instance;
        }

        /**
         * 
         */
        @Override
        public PVStructure request(PVStructure args) throws RPCRequestException {
            log.info(args.toString());
            NTURI uri = NTURI.wrap(args);

            String[] query = uri.getQueryNames();
            TransportClient client = ElasticSearchClientManager.getClient();
            try {
                BoolQueryBuilder qb = boolQuery();
                int size = 10000;
                int from = 0;
                for (String parameter : query) {
                    String value = uri.getQueryField(PVString.class, parameter).get();
                    if (value != null && !value.isEmpty()) {
                        switch (parameter) {
                        case "_name":
                            DisMaxQueryBuilder nameQuery = disMaxQuery();
                            for (String pattern : value.trim().split("\\|")) {
                                nameQuery.add(wildcardQuery("name", pattern.trim()));
                            }
                            qb.must(nameQuery);
                            break;
                        case "_tag":
                            for (String pattern1 : value.trim().split("\\&")) {
                                DisMaxQueryBuilder tagQuery = disMaxQuery();
                                for (String pattern2 : pattern1.trim().split("\\|")) {
                                    tagQuery.add(wildcardQuery("tags.name", pattern2.trim()));
                                }
                                qb.must(nestedQuery("tags", tagQuery));
                            }
                            break;
                        case "_size":
                            try {
                                size = Integer.valueOf(value.trim());
                            } catch (NumberFormatException e) {
                                log.warning("failed to parse the size: " + value);
                            }
                            break;
                        case "_from":
                            try {
                                from = Integer.valueOf(value.trim());
                            } catch (NumberFormatException e) {
                                log.warning("failed to parse the from: " + value);
                            }
                            break;
                        default:
                            DisMaxQueryBuilder propertyQuery = disMaxQuery();
                            for (String pattern : value.split("\\|")) {
                                propertyQuery.add(nestedQuery("properties",
                                        boolQuery().must(matchQuery("properties.name", parameter.trim()))
                                                .must(wildcardQuery("properties.value", pattern.trim()))));
                            }
                            qb.must(propertyQuery);
                            break;
                        }
                    }

                }

                SearchRequestBuilder builder = client.prepareSearch("channelfinder").setQuery(qb).setSize(size);
                if (from >= 0) {
                    builder.addSort(SortBuilders.fieldSort("name"));
                    builder.setFrom(from);
                }
                final SearchResponse qbResult = builder.execute().actionGet();

                final int resultSize = qbResult.getHits().hits().length;
                final Map<String, List<String>> channelTable = new HashMap<String, List<String>>();
                final Map<String, List<String>> channelPropertyTable = new HashMap<String, List<String>>();
                final Map<String, boolean[]> channelTagTable = new HashMap<String, boolean[]>();

                AtomicInteger counter = new AtomicInteger(0);

                channelTable.put("channelName", Arrays.asList(new String[resultSize]));
                channelTable.put("owner", Arrays.asList(new String[resultSize]));

                qbResult.getHits().forEach(hit -> {
                    hit.getFields().entrySet().forEach(System.out::println);

                    try {
                        XmlChannel ch = channelMapper.readValue(hit.source(), XmlChannel.class);
                        
                        int index = counter.getAndIncrement();

                        channelTable.get("channelName").set(index, ch.getName());
                        channelTable.get("owner").set(index, ch.getOwner());

                        ch.getTags().stream().forEach(t -> {
                            if (!channelTagTable.containsKey(t.getName())) {
                                channelTagTable.put(t.getName(), new boolean[resultSize]);
                            }
                            channelTagTable.get(t.getName())[index] = true;
                        });

                        ch.getProperties().stream().forEach(prop -> {
                            if (!channelPropertyTable.containsKey(prop.getName())) {
                                channelPropertyTable.put(prop.getName(), Arrays.asList(new String[resultSize]));
                            }
                            channelPropertyTable.get(prop.getName()).set(index, prop.getValue());
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                NTTableBuilder ntTableBuilder = NTTable.createBuilder();
                channelTable.keySet().forEach(name -> {
                    ntTableBuilder.addColumn(name, ScalarType.pvString);
                });
                channelPropertyTable.keySet().forEach(name -> {
                    ntTableBuilder.addColumn(name, ScalarType.pvString);
                });
                channelTagTable.keySet().forEach(name -> {
                    ntTableBuilder.addColumn(name, ScalarType.pvBoolean);
                });
                NTTable ntTable = ntTableBuilder.create();

                channelTable.entrySet().stream().forEach(col -> {
                    ntTable.getColumn(PVStringArray.class, col.getKey()).put(0, col.getValue().size(),
                            col.getValue().stream().toArray(String[]::new), 0);
                });
                
                channelPropertyTable.entrySet().stream().forEach(col -> {
                    ntTable.getColumn(PVStringArray.class, col.getKey()).put(0, col.getValue().size(),
                            col.getValue().stream().toArray(String[]::new), 0);
                });
                
                channelTagTable.entrySet().stream().forEach(col -> {
                    ntTable.getColumn(PVBooleanArray.class, col.getKey()).put(0, col.getValue().length,
                            col.getValue(), 0);
                });

                return ntTable.getPVStructure();
            } catch (Exception e) {
                log.log(Level.SEVERE, "Failed to complete request " + args + " for : " + SERVICE_NAME, e);
            } finally {
            }
            return null;
        }

        public void shutdown() {
            ElasticSearchClientManager.getClient().close();
            log.info("stop");
        }
    }

    abstract class OnlyXmlProperty {
        @JsonIgnore
        private List<XmlChannel> channels;
    }

    abstract class OnlyXmlTag {
        @JsonIgnore
        private List<XmlChannel> channels;
    }

    public static void main(String[] args) throws PVAException {

        RPCServer server = new RPCServer();

        // Cleanup connections and resources
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ChannelFinderServiceImpl.getInstance().shutdown();
                server.destroy();
            } catch (PVAException e) {
                log.log(Level.SEVERE, "Failed to close service : " + SERVICE_NAME, e);
            }
        }));

        System.out.println(SERVICE_NAME + " initializing...");
        server.registerService(SERVICE_NAME, ChannelFinderServiceImpl.getInstance());
        server.printInfo();
        System.out.println(SERVICE_NAME + " is operational.");

        server.run(0);

    }
}
