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

import java.io.OutputStream;
import java.util.Properties;

import javax.management.MBeanServerConnection;

/**
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
public interface JavaProcessDetails {
	
	public long getPid();
	
	public String getDescription();
	
	public JavaProcessId getJavaProcId();
	
	public Properties getSystemProperties();
	
	public Properties getAgentProperties();

	public String getVmFlag(String flag);
	
	public void jcmd(String command, Appendable output);

	public void sendAttachCommand(String command, Object[] args, OutputStream output, long timeoutMS);
	
	public MBeanServerConnection getMBeans();


}
