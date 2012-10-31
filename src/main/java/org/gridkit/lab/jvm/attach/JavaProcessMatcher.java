package org.gridkit.lab.jvm.attach;

public interface JavaProcessMatcher {

	public boolean evaluate(JavaProcessDetails details);
	
}
