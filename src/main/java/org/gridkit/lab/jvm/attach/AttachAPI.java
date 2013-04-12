/**
 * Copyright 2013 Alexey Ragozin
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

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
class AttachAPI {

    private static final LogStream LOG_ERROR = LogStream.error();
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
        	LOG_ERROR.log("Java home points to " + System.getProperty("java.home") + " make sure it is not a JRE path");
        	LOG_ERROR.log("Failed to add tools.jar to classpath", e);
        }
        started = true;
    };
	
	public static void ensureToolsJar() {
		if (!started) {
			LOG_ERROR.log("Attach API not initialized");
		}
	}	
}
