package org.gridkit.lab.jvm.attach;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttachAPI {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachManager.class);
	
    static {
        try {
            String javaHome = System.getProperty("java.home");
            String toolsJarURL = "file:" + javaHome + "/../lib/tools.jar";

            // Make addURL public
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            
            URLClassLoader sysloader = (URLClassLoader)ClassLoader.getSystemClassLoader();
            method.invoke(sysloader, (Object) new URL(toolsJarURL));
        } catch (Exception e) {
        	LOGGER.error("Failed to add tools.jar to classpath", e);
        	LOGGER.error("Java home points to " + System.getProperty("java.home") + " make sure it is not a JRE path");
        }
    }
	
	public static void ensureToolsJar() {
		// do nothing
	}	
}
