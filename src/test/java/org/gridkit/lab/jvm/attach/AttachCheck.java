package org.gridkit.lab.jvm.attach;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

import javax.management.JMX;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.gridkit.lab.jvm.attach.AttachManager;
import org.gridkit.lab.jvm.attach.JavaProcessId;
import org.gridkit.lab.jvm.attach.PatternJvmMatcher;
import org.junit.Test;


public class AttachCheck {

	@Test
	public void list_vm() {
		
		for(JavaProcessId jpid: AttachManager.listJavaProcesses()) {
			System.out.println(jpid);
		}		
	}

	@Test
	public void match_self_vm() {
		
		System.getProperties().put("test", "");
		
		PatternJvmMatcher matcher = new PatternJvmMatcher();
		matcher.matchProp("test", ".*");
		
		for(JavaProcessId jpid: AttachManager.listJavaProcesses(matcher)) {
			System.out.println(jpid);
		}		
	}

	@Test
	public void self_jmx() throws MalformedObjectNameException, NullPointerException {
		
		System.getProperties().put("test", "");
		
		PatternJvmMatcher matcher = new PatternJvmMatcher();
		matcher.matchProp("test", ".*");
		
		for(JavaProcessId jpid: AttachManager.listJavaProcesses(matcher)) {
			System.out.println(jpid);
			RuntimeMXBean runtime = JMX.newMXBeanProxy(AttachManager.getJmxConnection(jpid), ObjectName.getInstance(ManagementFactory.RUNTIME_MXBEAN_NAME), RuntimeMXBean.class);
			System.out.println(runtime.getName());
		}		
	}	
}
