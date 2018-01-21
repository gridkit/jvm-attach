/**
 * Copyright 2016 Alexey Ragozin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gridkit.lab.jvm.attach;

public class SimpleStackParser implements StackTraceElementParser {

    protected static final String NATIVE_METHOD = "Native Method";
    protected static final String UNKNOWN_SOURCE = "Unknown Source";

    protected static final int NO_LINE_NUMBER = 0;
    protected static final int NO_SOURCE = -1;
    protected static final int NATIVE = -2;
    
    private final boolean suppress;
    
    public SimpleStackParser(boolean suppressErrors) {
        this.suppress = suppressErrors;
    }

    @Override
    public StackTraceElement paser(CharSequence line) {
        StringBuilder sb = new StringBuilder(line.length());
        int dot1 = -1;
        int dot2 = -1;
        int n = 0;
        while(true) {
            char ch = line.charAt(n);
            if (ch == '(') {
                break;
            }
            if (ch == '.') {
                dot2 = dot1;
                dot1 = n;
            }
            sb.append(ch);
            ++n;
            if (n >= line.length()) {
                if (suppress) {
                    return null;
                }
                else {
                    throw new IllegalArgumentException("Cannot parse [" + line + "]");
                }
            }
        }
        if (dot1 == -1) {
            if (suppress) {
                return null;
            }
            else {
                throw new IllegalArgumentException("Cannot parse [" + line + "]");
            }
        }
        String pref = null;
        String cn = null;
        String mn = null;
        if (dot2 != -1) {
            pref = sb.substring(0, dot2);
            cn = sb.substring(dot2 + 1, dot1);
            mn = sb.substring(dot1 + 1);
        }
        else {
            cn = sb.substring(0, dot1);
            mn = sb.substring(dot1 + 1);
        }
        sb.setLength(0);
        int col = -1;
        ++n;
        int off = n;
        while(true) {
            char ch = line.charAt(n);
            if (ch == ')') {
                break;
            }
            if (ch == ':') {
                col = n - off;
            }
            sb.append(ch);
            ++n;
            if (n >= line.length()) {
                if (suppress) {
                    return null;
                }
                else {
                    throw new IllegalArgumentException("Cannot parse [" + line + "]");
                }
            }
        }
        String file = null;
        int lnum = -1;
        if (col != -1) {
            file = sb.substring(0, col);
            try {
                lnum = Integer.parseInt(sb.substring(col + 1));
            }
            catch(NumberFormatException e) {
                if (suppress) {
                    return null;
                }
                else {
                    throw new IllegalArgumentException("Number format exception '" + e.getMessage() + "' parsing [" + line + "]");
                }
            }
        }
        else {
            file = sb.toString();
            if (file.equals(NATIVE_METHOD)) {
                file = null;
                lnum = -2;
            }
            else if (file.equals(UNKNOWN_SOURCE)) {
                file = null;
            }
            
        }
        return new StackTraceElement(pref + "." + cn, mn, file, lnum);
    }    
}