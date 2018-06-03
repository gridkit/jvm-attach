package org.gridkit.lab.jvm.attach;

import org.junit.Test;


public class AttachListCheck {

	@Test
	public void list_vm() {
		
		for(JavaProcessId jpid: AttachManager.listJavaProcesses()) {
			System.out.println(jpid);
		}		
	}
}
