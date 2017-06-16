package org.epics.channelfinder;

import org.epics.channelfinder.example.PopulateExampleDb;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * A test suite to run the integration tests which require the setup of a large
 * pre configured channel database
 * 
 * @author Kunal Shroff
 *
 */

@RunWith(Suite.class)
@Suite.SuiteClasses({  
                     CFAdvanceQueryIT.class,
                     AsyncServiceIT.class
                       })
public class CFPopulatedIT {


    /**
     * Create a single cell version of the test database 3000 channels with 40
     * tags and 60 properties
     */
    @BeforeClass
    public static void setup() {
        PopulateExampleDb.createDB(2);
        try {
            Thread.sleep(60000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    /**
     * Cleanup the Database
     */
    @AfterClass
    public static void cleanupDB() {
        PopulateExampleDb.cleanupDB();
    }
}
