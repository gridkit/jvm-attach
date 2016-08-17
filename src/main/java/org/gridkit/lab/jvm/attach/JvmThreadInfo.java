package org.gridkit.lab.jvm.attach;

import java.lang.Thread.State;

public class JvmThreadInfo {
    
    private final String name;
    private final boolean isJavaThread;
    private final boolean isDaemon;
    private final long nativeId;
    private final State javaThreadState;
    private final String extThreadState;
    private StackTraceElement[] javaStackTrace;
    
    public JvmThreadInfo(String name, boolean isDaemon, boolean isJavaThread, long nativeId, State javaThreadState, String extThreadState) {
        this.name = name;
        this.isJavaThread = isJavaThread;
        this.isDaemon = isDaemon;
        this.nativeId = nativeId;
        this.javaThreadState = javaThreadState;
        this.extThreadState = extThreadState;
    }
    
    public String getName() {
        return name;
    }

    public boolean isJavaThread() {
        return isJavaThread;
    }

    public boolean isDaemon() {
        return isDaemon;
    }

    public long getNativeId() {
        return nativeId;
    }
    
    public State getJavaThreadState() {
        return javaThreadState;
    }

    public String getExtThreadState() {
        return extThreadState;
    }

    public StackTraceElement[] getJavaStackTrace() {
        return javaStackTrace;
    }
    
    public void setJavaStackTrace(StackTraceElement[] trace) {
        this.javaStackTrace = trace;
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        sb.append(name);
        sb.append("\"");
        if (isDaemon) {
            sb.append(" daemon");
        }
        if (nativeId > 0) {
            sb.append(" nid=0x").append(Long.toHexString(nativeId));
        }
        if (nativeId > 0) {
            sb.append(" nid=0x").append(Long.toHexString(nativeId));
        }
        if (extThreadState != null) {
            sb.append(" ").append(extThreadState);
        }
        if (isJavaThread) {
            if (javaStackTrace != null && javaStackTrace.length > 0) {
                sb.append(" java stack (").append(javaStackTrace.length).append(")");
            }            
            else { 
                sb.append(" no java stack");
            }
        }
        else {
            sb.append(" non-java thread");
        }
    
        return sb.toString();
    }
}
