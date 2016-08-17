package org.gridkit.lab.jvm.attach;

import java.lang.management.ManagementFactory;

import org.junit.Test;

public class ThreadDumpCheck {
    
    private int pid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        name = name.substring(0, name.indexOf("@"));
        return Integer.parseInt(name);
    }
    
    @Test
    public void test_thread_dump() throws Exception {
        String[] args = {};
        StringBuilder sb = new StringBuilder();
        AttachManager.getThreadDump(pid(), args, sb, 30000);
        System.out.println(sb);
    }

    @Test
    public void test_parsed_thread_dump() throws Exception {
        spawnBusyThread("Blocker-1");
        spawnBusyThread("Blocker-2");
        spawnBusyThread("Blocker-3");
        spawnBusyThread("Blocker-4");
        
        String[] args = {};
        StringBuilder sb = new StringBuilder();
        AttachManager.getThreadDump(pid(), args, sb, 30000);
        System.out.println(sb);
        
        System.out.println("\nParsed dump\n");
        
        JvmThreadInfoParser tp = new JvmThreadInfoParser(true);
        AttachManager.getThreadDump(pid(), args, tp, 30000);
        for(JvmThreadInfo ti: tp.getThreads()) {
            System.out.println(ti);
            if (ti.getJavaStackTrace() != null) {
                for(StackTraceElement e: ti.getJavaStackTrace()) {
                    System.out.println("  " + e);
                }
            }
            System.out.println();
        }
    }

    @Test
    public void test_thread_enumerator() throws Exception {
        String[] args = {};
        JvmThreadInfoParser tp = new JvmThreadInfoParser();
        AttachManager.getThreadDump(pid(), args, tp, 30000);
        for(JvmThreadInfo ti: tp.getThreads()) {
            System.out.print("[" + ti.getNativeId() + "] ");
            System.out.println(ti);
        }
    }
    
    public synchronized void busyCall() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
        }
    }
    
    public void spawnBusyThread(String name) {
        Thread t = new Thread(name) {
            @Override
            public void run() {
                while(true) {
                    busyCall();
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }
}
