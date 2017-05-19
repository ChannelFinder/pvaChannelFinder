package org.epics.channelfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.epics.nt.NTTable;
import org.epics.nt.NTTableBuilder;
import org.epics.pvdata.pv.PVStringArray;
import org.epics.pvdata.pv.ScalarType;
import org.junit.Test;

/**
 * Unit test for the parse utility class
 * @author Kunal Shroff
 *
 */
public class XmlUtilTest {

    /**
     * rudimentary test for simple NTTable parsing
     */
    @Test
    public void simple(){
        NTTableBuilder ntTableBuilder = NTTable.createBuilder();
        ntTableBuilder.addColumn("channelName", ScalarType.pvString);
        ntTableBuilder.addColumn("owner", ScalarType.pvString);
        NTTable table = ntTableBuilder.create();
        table.getColumn(PVStringArray.class, "channelName").put(0, 3, new String[] {"ch1", "ch2", "ch3"}, 0);
        table.getColumn(PVStringArray.class, "owner").put(0, 3, new String[] {"owner", "owner", "owner"}, 0);
        
        try {
            List<XmlChannel> result = XmlUtil.parse(table.getPVStructure());
            assertEquals(result, Arrays.asList(new XmlChannel("ch1", "owner"), 
                                               new XmlChannel("ch2", "owner"),
                                               new XmlChannel("ch3", "owner")));

        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
