package org.epics.channelfinder;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.epics.channelfinder.example.PopulateExampleDb;
import org.epics.nt.NTURI;
import org.epics.nt.NTURIBuilder;
import org.epics.pvaccess.client.rpc.RPCClientImpl;
import org.epics.pvaccess.server.rpc.RPCRequestException;
import org.epics.pvdata.pv.PVStructure;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;



public class AsyncServiceIT {

    private static Logger log = Logger.getLogger(AsyncServiceIT.class.getCanonicalName());

    private static int numberOfCells = 2;
    
    @BeforeClass
    public static void setup() {
        PopulateExampleDb.createDB(numberOfCells);
    }

    @Test
    public void parallelTest() {

        List<Integer> tokens = Arrays.asList(1,2,5,10,20,50,100,500); 

        int queryCount = 1000;
        final ExecutorService scheduler = Executors.newScheduledThreadPool(100);
        List<Future<Boolean>> futures = new ArrayList<>();
        long start = System.currentTimeMillis();
        for (int i = 0; i < queryCount; i++) {
            futures.add(scheduler.submit(() -> {

                RPCClientImpl client = new RPCClientImpl(ChannelFinderService.SERVICE_NAME);
                try {
                    String name_search_pattern = "SR*C"+String.format("%03d", ThreadLocalRandom.current().nextInt(1, numberOfCells))+"*";
                    String propName = "group" + ThreadLocalRandom.current().nextInt(9);
                    Integer propValue = tokens.get(ThreadLocalRandom.current().nextInt(7));
                    NTURIBuilder uriBuilder = NTURI.createBuilder()
                            .addQueryString("_name")
                            .addQueryString(propName);
                    NTURI uri = uriBuilder.create();

                    uri.getPVStructure().getStringField("scheme").put("pva");
                    uri.getPVStructure().getStringField("path").put("channels");
                    uri.getQuery().getStringField("_name").put(name_search_pattern);
                    uri.getQuery().getStringField(propName).put(String.valueOf(propValue));

                    PVStructure result = client.request(uri.getPVStructure(), 3.0);
//                    System.out.println(result);
                    List<XmlChannel> chs = XmlUtil.parse(result);
                    if (chs.size() != propValue){
//                        System.out.println("Failed to execute query : " + uri + " expected " + propValue + " " + chs.size());
                        throw new Exception("Failed to execute query : " + uri + " expected " + propValue + " " + chs.size());
                    }
                    return chs.size() == propValue;
                } catch (RPCRequestException ex) {
                    // The client connected to the server, but the server
                    // request method issued its
                    // standard summary exception indicating it couldn't
                    // complete the requested task.
                    throw new ExecutionException("Acquisition was not successful, " + "service responded with an error: " + ex.getMessage(), ex);
                } catch (IllegalStateException ex) {
                    // The client failed to connect to the server. The server
                    // isn't running or
                    // some other network related error occurred.
                    throw new ExecutionException("Acquisition was "
                            + "not successful, " + "failed to connect: " + ex.getMessage(), ex);
                } catch (Exception e) {
                    throw new ExecutionException(e);
                } finally {
                    client.destroy();
                }
            }));
        }
        int counter =  0;
        int failCounter = 0;
        for (Future<Boolean> future : futures) {
            try {
                if(future.get()){
                    counter++;
                }else{
                    failCounter++;
                }
            } catch (InterruptedException | ExecutionException e) {
                log.log(Level.WARNING, e.getMessage(), e);
                fail(e.getMessage());
            }
        }
        log.info("Successfully complete: " + counter + " Failed: " + failCounter);
        log.info("ChannelFinderService "+ queryCount +" query time: " + (System.currentTimeMillis() - start));
        assertTrue("Failed to complete all Async queries reliably", failCounter<=0);
    }
    
    @AfterClass
    public static void cleanup(){
        // Remove all the channels, tags, and properties created for testing.
        PopulateExampleDb.cleanupDB();
    }

}
