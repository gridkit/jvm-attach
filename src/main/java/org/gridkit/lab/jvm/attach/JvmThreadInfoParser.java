package org.gridkit.lab.jvm.attach;

import java.io.IOException;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JvmThreadInfoParser implements Appendable {

    private List<JvmThreadInfo> threads = new ArrayList<JvmThreadInfo>();    
    private StringBuilder line = new StringBuilder();
    private boolean threadPending = false;
    private String threadName;
    private long nativeThreadId;
    private boolean isDaemon;
    private String extThreadState;

    private boolean parseTrace = false;
    private StackTraceElementParser stackParser;
    private boolean expectTrace = false;
    private List<StackTraceElement> lastTrace = new ArrayList<StackTraceElement>();
    
    private Matcher threadState = Pattern.compile("\\s+ java\\.lang\\.Thread\\.State:\\s+([A-Z_]+)").matcher("");
    private Matcher threadLine = Pattern.compile("\\s+(daemon)?.+nid=0x([0-9a-fA-F]+)\\s+([^\\[]*)(\\[0x([a-fA-F0-9]*)\\])?").matcher("");
    
    public JvmThreadInfoParser() {
        this(false);
    }
    
    public JvmThreadInfoParser(boolean parseTrace) {
        this.parseTrace = parseTrace;
        if (parseTrace) {
            stackParser = StackTraceElementParser.DEFAULT;
        }
    }
    
    public void setStackElementParser(StackTraceElementParser parser) {
        stackParser = parser;
    }
    
    @Override
    public Appendable append(CharSequence csq) throws IOException {
        append(csq, 0, csq.length());
        return this;
    }
    
    @Override
    public Appendable append(CharSequence csq, int start, int end) throws IOException {
        for(int i = start; i != end; ++i) {
            append(csq.charAt(i));
        }
        return this;
    }
    
    @Override
    public Appendable append(char c) {
        if (c == '\n') {
            lineComplete();
        }
        else {
            line.append(c);
        }
        return this;
    }

    private void lineComplete() {
        if (line.length() > 0) {
            if (line.charAt(line.length() - 1) == '\r') {
                line.setLength(line.length() - 1);
            }
            if (threadPending) {
                threadState.reset(line);
                if (threadState.lookingAt()) {
                    String tstate = threadState.group(1);
                    State state = null;
                    try {
                        state = State.valueOf(tstate);
                    } catch (Exception e) {
                        // ignore
                    }
                    appendJavaThread(state);
                }
                else {
                    appendNonJavaThread();                    
                }
            }
            else {
                if (line.length() > 0) {
                    if (line.charAt(0) == '"') {
                        int n = line.lastIndexOf("\"");
                        threadLine.reset(line);
                        if (threadLine.find(n + 1)) {
                            threadName = line.substring(1, n);
                            isDaemon = threadLine.group(1) != null;
                            nativeThreadId = 0;
                            try {
                                nativeThreadId = Long.parseLong(threadLine.group(2), 16);
                            } catch (NumberFormatException e) {
                                // ignore
                            }
                            extThreadState = threadLine.group(3);
                            if (extThreadState != null) {
                                extThreadState = extThreadState.trim();
                            }
                            completeStackTrace();
                            threadPending = true;
                            expectTrace = true;
                        }
                    }
                }
            }
            if (parseTrace && expectTrace) {
                int n = 0;
                for(; n < line.length(); ++n) {
                    if (!Character.isWhitespace(line.charAt(n))) {
                        break;
                    }
                }
                if (n < line.length() + 3) {
                    if ((line.charAt(n) == 'a') && (line.charAt(n + 1) == 't') && (line.charAt(n + 2) == ' ')) {
                        n += 3;
                        StackTraceElement ste = stackParser.paser(line.substring(n));
                        if (ste != null) {
                            lastTrace.add(ste);
                        }
                    }
                }
            }
        }
        else {
            // Empty line
            if (threadPending) {
                appendNonJavaThread();
            }
            
            completeStackTrace();
        }
        line.setLength(0);
    }

    private void completeStackTrace() {
        if (parseTrace) {
            if (!lastTrace.isEmpty() && !threads.isEmpty()) {
                StackTraceElement[] trace = lastTrace.toArray(new StackTraceElement[lastTrace.size()]);
                threads.get(threads.size() - 1).setJavaStackTrace(trace);
            }
            lastTrace.clear();
        }        
        expectTrace = false;
    }

    private void appendJavaThread(State state) {
        threads.add(new JvmThreadInfo(threadName, isDaemon, true, nativeThreadId, state, extThreadState));
        threadPending = false;
    }

    private void appendNonJavaThread() {
        threads.add(new JvmThreadInfo(threadName, isDaemon, false, nativeThreadId, null, extThreadState));
        threadPending = false;
    }
    
    public JvmThreadInfo[] getThreads() {
        append('\n');
        if (threadPending) {
            appendNonJavaThread();
        }
        return threads.toArray(new JvmThreadInfo[threads.size()]);
    }
}
