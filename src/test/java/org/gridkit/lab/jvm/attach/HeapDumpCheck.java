package org.gridkit.lab.jvm.attach;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;

import org.junit.Test;

public class HeapDumpCheck {

    private int pid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        name = name.substring(0, name.indexOf("@"));
        return Integer.parseInt(name);
    }
    
    @Test
    public void checkAllDump() throws IOException {
        System.out.println(HeapDumper.dumpAll(pid(), "target/all-dump.hprof", 60000));
        System.out.println("Dump size: " + (new File("target/all-dump.hprof").length() >> 10) + "k");
    }

    @Test
    public void checkLiveDump() throws IOException {
        System.out.println(HeapDumper.dumpLive(pid(), "target/live-dump.hprof", 60000));
        System.out.println("Dump size: " + (new File("target/live-dump.hprof").length() >> 10) + "k");
    }
    
}
