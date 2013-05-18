package org.gridkit.lab.jvm.perfdata;

import java.lang.management.ManagementFactory;

import org.gridkit.lab.jvm.perfdata.JStatData.StringCounter;
import org.junit.Assert;
import org.junit.Test;

public class TestPerfData {

    private int pid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        name = name.substring(0, name.indexOf("@"));
        return Integer.parseInt(name);
    }

    @Test
    public void verify_self_attach() {
    	JStatData data = JStatData.connect(pid());
    	for(JStatData.Counter<?> c: data.getAllCounters().values()) {
    		Assert.assertNotNull(c.getName());
    		Assert.assertNotNull(c.getUnits());
    		Assert.assertNotNull(c.getVariability());
    		Assert.assertNotNull(c.getValue());
    		System.out.println(c);
    		if (c instanceof StringCounter) {
    			String val = (String) c.getValue();
    			Assert.assertTrue(val.indexOf(0) < 0);
    		}
    	}
    }

    @Test
    public void verify_self_attach_reentrancy() {
    	JStatData data = JStatData.connect(pid());
    	for(JStatData.Counter<?> c: data.getAllCounters().values()) {
    		Assert.assertNotNull(c.getName());
    		Assert.assertNotNull(c.getUnits());
    		Assert.assertNotNull(c.getVariability());
    		Assert.assertNotNull(c.getValue());
    		if (c instanceof StringCounter) {
    			String val = (String) c.getValue();
    			Assert.assertTrue(val.indexOf(0) < 0);
    		}
    	}
    }
}
