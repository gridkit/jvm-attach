package org.gridkit.lab.jvm.attach;

import java.util.Properties;

import javax.management.MBeanServerConnection;

public interface JavaProcessDetails {
	
	public long getPid();
	
	public String getDescription();
	
	public JavaProcessId getJavaProcId();
	
	public Properties getSystemProperties();
	
	public Properties getAgentProperties();

	public String getVmFlag(String flag);
	
	public MBeanServerConnection getMBeans();

}
