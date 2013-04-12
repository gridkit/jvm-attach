package org.gridkit.lab.jvm.attach;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AttachAPI {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachManager.class);
    private static boolean started;
	
    static {
        try {
            String javaHome = System.getProperty("java.home");
            String toolsJarURL = "file:" + javaHome + "/../lib/tools.jar";

            // Make addURL public
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            
            URLClassLoader sysloader = (URLClassLoader)ClassLoader.getSystemClassLoader();
            if (sysloader.getResourceAsStream("/com/sun/tools/attach/VirtualMachine.class") == null) {
            	method.invoke(sysloader, (Object) new URL(toolsJarURL));
	            Thread.currentThread().getContextClassLoader().loadClass("com.sun.tools.attach.VirtualMachine");
	            Thread.currentThread().getContextClassLoader().loadClass("com.sun.tools.attach.AttachNotSupportedException");
            }
            
        } catch (Exception e) {
        	LOGGER.error("Java home points to " + System.getProperty("java.home") + " make sure it is not a JRE path");
        	LOGGER.error("Failed to add tools.jar to classpath", e);
        }
        started = true;
    };
	
	public static void ensureToolsJar() {
		if (!started) {
			System.err.println("Attach API not initialized");
		}
	}	
}
