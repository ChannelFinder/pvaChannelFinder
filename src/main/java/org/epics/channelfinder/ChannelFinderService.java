package org.epics.channelfinder;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.disMaxQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.epics.pvaccess.server.rpc.RPCResponseCallback;
import org.epics.pvaccess.server.rpc.RPCServer;
import org.epics.pvaccess.server.rpc.RPCServiceAsync;
import org.epics.pvdata.factory.StatusFactory;
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
            .addMixIn(XmlProperty.class, OnlyXmlProperty.class).addMixIn(XmlTag.class, OnlyXmlTag.class);

    private static class ChannelFinderServiceImpl implements RPCServiceAsync {

        private static final ChannelFinderServiceImpl instance = new ChannelFinderServiceImpl();

        private ChannelFinderServiceImpl() {
            log.info("start");
        }

        public static ChannelFinderServiceImpl getInstance() {
            return instance;
        }

        private final ExecutorService pool = Executors.newScheduledThreadPool(50);

        @Override
        public void request(PVStructure args, RPCResponseCallback call) {
            log.fine(args.toString());
//            pool.execute(new HandlerQuery(args, call));
            HandlerQuery query = new HandlerQuery(args, call);
            query.run();
        }

        private static class HandlerQuery implements Runnable {

            private final RPCResponseCallback callback;
            private final PVStructure args;

            public HandlerQuery(PVStructure args, RPCResponseCallback callback) {
                this.callback = callback;
                this.args = args;
            }

            @Override
            public void run() {

                NTURI uri = NTURI.wrap(args);
                log.info(Thread.currentThread().getName().toString());

                String[] query = uri.getQueryNames();
                TransportClient client = ElasticSearchClientManager.getClient();

                try {
                    BoolQueryBuilder qb = boolQuery();
                    int size = 10000;
                    int from = 0;
                    Optional<String> sortField = Optional.empty();
                    final Set<String> filteredColumns = new HashSet<>();
                    
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
                                    sortField = Optional.of("name");
                                } catch (NumberFormatException e) {
                                    log.warning("failed to parse the size: " + value);
                                }
                                break;
                            case "_from":
                                try {
                                    from = Integer.valueOf(value.trim());
                                    sortField = Optional.of("name");
                                } catch (NumberFormatException e) {
                                    log.warning("failed to parse the from: " + value);
                                }
                                break;
                            case "_filter":
                                try {
                                    filteredColumns.addAll(Arrays.asList(value.trim().split(",")));
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
                        builder.setFrom(from);
                    }

                    sortField.ifPresent((s) -> {
                        builder.addSort(SortBuilders.fieldSort(s));
                    });
                    final SearchResponse qbResult = builder.execute().actionGet();

                    final int resultSize = qbResult.getHits().hits().length;
                    final Map<String, List<String>> channelTable = new HashMap<String, List<String>>();
                    final Map<String, List<String>> channelPropertyTable = new HashMap<String, List<String>>();
                    final Map<String, boolean[]> channelTagTable = new HashMap<String, boolean[]>();

                    AtomicInteger counter = new AtomicInteger(0);

                    channelTable.put("channelName", Arrays.asList(new String[resultSize]));
                    channelTable.put("owner", Arrays.asList(new String[resultSize]));

                    qbResult.getHits().forEach(hit -> {
                        try {
                            XmlChannel ch = channelMapper.readValue(hit.source(), XmlChannel.class);

                            int index = counter.getAndIncrement();

                            channelTable.get("channelName").set(index, ch.getName());
                            channelTable.get("owner").set(index, ch.getOwner());

                            if (!filteredColumns.contains("ALL")) {
                                ch.getTags().stream().filter((tag) -> {
                                    return filteredColumns.isEmpty() || filteredColumns.contains(tag.getName());
                                }).forEach(t -> {
                                    if (!channelTagTable.containsKey(t.getName())) {
                                        channelTagTable.put(t.getName(), new boolean[resultSize]);
                                    }
                                    channelTagTable.get(t.getName())[index] = true;
                                });

                                ch.getProperties().stream().filter((prop) -> {
                                    return filteredColumns.isEmpty() || filteredColumns.contains(prop.getName());
                                }).forEach(prop -> {
                                    if (!channelPropertyTable.containsKey(prop.getName())) {
                                        channelPropertyTable.put(prop.getName(), Arrays.asList(new String[resultSize]));
                                    }
                                    channelPropertyTable.get(prop.getName()).set(index, prop.getValue());
                                });
                            }
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

                    log.fine(ntTable.toString());
                    this.callback.requestDone(StatusFactory.getStatusCreate().getStatusOK(), ntTable.getPVStructure());
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Failed to complete request " + args + " for : " + SERVICE_NAME, e);
                    this.callback.requestDone(StatusFactory.getStatusCreate().getStatusOK(), null);
                } finally {
                }
            }
        }

        public void shutdown() {
            log.info("shutting down service.");
            ElasticSearchClientManager.close();
            log.info("shut down elastic client.");
            pool.shutdown();
            // Disable new tasks from being submitted
            try {
                // Wait a while for existing tasks to terminate
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    pool.shutdownNow(); // Cancel currently executing tasks
                    // Wait a while for tasks to respond to being cancelled
                    if (!pool.awaitTermination(60, TimeUnit.SECONDS))
                        System.err.println("Pool did not terminate");
                }
            } catch (InterruptedException ie) {
                // (Re-)Cancel if current thread also interrupted
                pool.shutdownNow();
                // Preserve interrupt status
                Thread.currentThread().interrupt();
            }
            log.info("completed shut down.");
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
            log.info("Shutting down service " + SERVICE_NAME);
            try {
                ChannelFinderServiceImpl.getInstance().shutdown();
                server.destroy();
                log.info(SERVICE_NAME + " Shutdown complete.");
            } catch (PVAException e) {
                log.log(Level.SEVERE, "Failed to close service : " + SERVICE_NAME, e);
            }
        }));

        log.info(SERVICE_NAME + " initializing...");
        server.registerService(SERVICE_NAME, ChannelFinderServiceImpl.getInstance());
        server.printInfo();
        log.info(SERVICE_NAME + " is operational.");

        server.run(0);

    }
}
