package org.epics.channelfinder.example;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

import org.epics.channelfinder.ChannelFinderService;
import org.epics.channelfinder.XmlChannel;
import org.epics.channelfinder.XmlUtil;
import org.epics.nt.NTURI;
import org.epics.pvaccess.client.rpc.RPCClientImpl;
import org.epics.pvaccess.server.rpc.RPCRequestException;
import org.epics.pvdata.pv.PVStructure;

public class ExampleChannelFinderClient {

    public static void main(String[] args) {

        // Setup for creating some test data
        PopulateExampleDb.createDB(1);

        RPCClientImpl client = new RPCClientImpl(ChannelFinderService.SERVICE_NAME);

        try {
            String name_search_pattern = "SR*C001*";
            String propName = "group9";
            NTURI uri = NTURI.createBuilder().addQueryString("_name").create();

            /**
             *  Create a query for searching based on the name
             *  
             *  epics:nt/NTURI:1.0
             *      string scheme pva
             *      string path channels
             *      structure query
             *          string _name SR*C001*
             */

            uri = NTURI.createBuilder().addQueryString("_name").create();
            uri.getPVStructure().getStringField("scheme").put("pva");
            uri.getPVStructure().getStringField("path").put("channels");
            uri.getQuery().getStringField("_name").put(name_search_pattern);
            PVStructure result = client.request(uri.getPVStructure(), 3.0);
            Collection<XmlChannel> channels = XmlUtil.parse(result);

            /** Create a query for searching based on property values, the property values can be OR'd
             *
             * epics:nt/NTURI:1.0
             *     string scheme pva
             *     string path channels
             *     structure query
             *         string group1 10|2
             */
            uri = NTURI.createBuilder().addQueryString(propName).create();
            uri.getPVStructure().getStringField("scheme").put("pva");
            uri.getPVStructure().getStringField("path").put("channels");
            uri.getQuery().getStringField(propName).put("10|2");
            result = client.request(uri.getPVStructure(), 3.0);
            channels = XmlUtil.parse(result);

            /** Create a query for searching based on tags
             * 
             *  epics:nt/NTURI:1.0
             *      string scheme pva
             *      string path channels
             *      structure query
             *          string _tag group8_50
            **/
            uri = NTURI.createBuilder().addQueryString("_tag").create();
            uri.getPVStructure().getStringField("scheme").put("pva");
            uri.getPVStructure().getStringField("path").put("channels");
            uri.getQuery().getStringField("_tag").put("group8_50");
            result = client.request(uri.getPVStructure(), 3.0);
            channels = XmlUtil.parse(result);

            /** Create a query for searching based on names, tags, and property value
             * 
             *  epics:nt/NTURI:1.0
             *      string scheme pva
             *      string path channels
             *      structure query
             *          string _name SR*C001*
             *          string _tag group8_50
             *          string group1 500
            **/
            uri = NTURI.createBuilder().addQueryString("_name").addQueryString("_tag").addQueryString(propName).create();
            uri.getPVStructure().getStringField("scheme").put("pva");
            uri.getPVStructure().getStringField("path").put("channels");

            uri.getQuery().getStringField("_name").put(name_search_pattern);
            uri.getQuery().getStringField("_tag").put("group8_50");
            uri.getQuery().getStringField(propName).put(String.valueOf(500));
            result = client.request(uri.getPVStructure(), 3.0);
            channels = XmlUtil.parse(result);

        } catch (RPCRequestException ex) {
            // The client connected to the server, but the server
            // request method issued its
            // standard summary exception indicating it couldn't
            // complete the requested task.
            System.err.println("Acquisition was not successful, " + "service responded with an error: " + ex.getMessage());
        } catch (IllegalStateException ex) {
            // The client failed to connect to the server. The server
            // isn't running or
            // some other network related error occurred.
            System.err.println("Acquisition was not successful, " + "failed to connect: " + ex.getMessage());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            PopulateExampleDb.cleanupDB();
            client.destroy();
        }

    }
}
