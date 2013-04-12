package org.gridkit.lab.jvm.attach;

import java.lang.management.ManagementFactory;
import java.util.List;

import org.junit.Test;

public class ClassHistoCheck {
    
    private int pid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        name = name.substring(0, name.indexOf("@"));
        return Integer.parseInt(name);
    }
    
    @Test
    public void test_heap_histo() throws Exception {
        String[] args = {};
        List<String> histo = AttachManager.getHeapHisto(pid(), args, 10000);
        for(String line: histo) {
            System.out.println(line);
        }
    }

    @Test
    public void test_heap_histo_live() throws Exception {
    	String[] args = {"-live"};
    	List<String> histo = AttachManager.getHeapHisto(pid(), args, 10000);
    	for(String line: histo) {
    		System.out.println(line);
    	}
    }

    @Test
    public void test_heap_histo_object() throws Exception {
    	String[] args = {"-live"};
    	List<String> histo = AttachManager.getHeapHisto(pid(), args, 10000);
    	for(String line: histo) {
    		System.out.println(line);
    	}
    	HeapHisto hh = HeapHisto.parse(histo);
    	System.out.println();
    	System.out.println(hh.print());
    }

    @Test
    public void test_heap_histo_new() throws Exception {
    	String[] all = {"-all"};
    	String[] live = {"-live"};
    	List<String> hall = AttachManager.getHeapHisto(pid(), all, 10000);
    	List<String> hlive = AttachManager.getHeapHisto(pid(), live, 10000);

    	HeapHisto hhall = HeapHisto.parse(hall);
    	HeapHisto hhlive = HeapHisto.parse(hlive);
    	
    	System.out.println("Young garbage:");    	
    	System.out.println(HeapHisto.subtract(hhall, hhlive).print(20));

    	System.out.println("Old objects:");    	
    	System.out.println(hhall.print(20));
    }

    @Test
    public void test_heap_histo_dead() throws Exception {
    	HeapHisto dead = HeapHisto.getHistoDead(pid(), 30000);
    	System.out.println(dead.print());
    }

}
