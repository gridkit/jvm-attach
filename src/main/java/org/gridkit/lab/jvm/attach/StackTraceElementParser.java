package org.gridkit.lab.jvm.attach;

public interface StackTraceElementParser {
    
    public static StackTraceElementParser DEFAULT = new SimpleStackParser(true);
    
    public StackTraceElement paser(CharSequence line);
}
