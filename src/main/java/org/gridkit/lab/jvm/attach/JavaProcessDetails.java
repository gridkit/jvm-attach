package org.gridkit.lab.jvm.attach;

import java.util.Properties;

public interface JavaProcessDetails {
	
	public long getPid();
	
	public String getDescription();
	
	public Properties getSystemProperties();

}
