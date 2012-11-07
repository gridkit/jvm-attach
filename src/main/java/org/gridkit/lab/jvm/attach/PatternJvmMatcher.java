package org.gridkit.lab.jvm.attach;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternJvmMatcher implements JavaProcessMatcher, Serializable {        
    
	private static final long serialVersionUID = 20121106L;
	
	private final Map<String, Pattern> patterns = new LinkedHashMap<String, Pattern>();

    public void matchVmName(String pattern) {
    	matchProp(":name", pattern);
    }
    
    public void matchProp(String prop, String pattern) {
    	Pattern p = Pattern.compile(pattern);
    	patterns.put(prop, p);
    }

    public void matchPropExact(String prop, String pattern) {
    	matchProp(prop, Pattern.quote(pattern));
    }

    @Override
	public boolean evaluate(JavaProcessDetails proc) {
    	if (patterns.containsKey(":name")) {
    		if (!match(":name", proc.getDescription())) {
    			return false;
    		}
    	}
        
        Properties props = proc.getSystemProperties();
        if (props == null) {
        	return false;
        }
        
        for(String prop: patterns.keySet()) {
        	if (!prop.startsWith(":")) {
        		if (!match(prop, props.getProperty(prop))) {
        			return false;
        		}
        	}
        }
            
        return true;
    }
    
    private boolean match(String prop, String value) {
    	if (value == null) {
    		return false;
    	}
		Matcher matcher = patterns.get(prop).matcher(value);
		return matcher.matches();
	}


	@Override
    public String toString() {
        return String.format("%s%s", getClass().getSimpleName(), patterns.toString());
    }
}	
