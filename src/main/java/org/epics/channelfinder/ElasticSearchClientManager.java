
package org.epics.channelfinder;
/**
 * 
 */

import java.util.logging.Level;
import java.util.logging.Logger;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

/**
 * @author Kunal Shroff {@literal <shroffk@bnl.gov>}
 *
 */
public class ElasticSearchClientManager {

    private static Logger log = Logger.getLogger(ElasticSearchClientManager.class.getCanonicalName());

    private static final ElasticSearchClientManager client = new ElasticSearchClientManager();
    private TransportClient searchClient;

    private ElasticSearchClientManager() {
        log.info("Initializing a new Transport clients.");
        try {
            searchClient = new TransportClient();
            Settings settings = searchClient.settings();
            String host = settings.get("network.host");
            int port = Integer.valueOf(settings.get("transport.tcp.port"));
            searchClient.addTransportAddress(new InetSocketTransportAddress(host, port));
        } catch (NumberFormatException | ElasticsearchException e) {
            log.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    public static TransportClient getClient() {
        return client.searchClient;
    }

    public void close() {
        log.info("Close the default Transport clients.");
        if (searchClient != null) {
            try {
                searchClient.close();
            } catch (Exception e) {
                log.log(Level.SEVERE, e.getMessage(), e);
            }
        }
    }

}
