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

/**
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
abstract class LogStream {

	private static String getCallerName() {
		String name = Thread.currentThread().getStackTrace()[3].getClassName();
		return name;
	}
	
	public static LogStream info() {
		String name = getCallerName();
		try {
			return Slf4JLogger.info(name);
		}
		catch(NoClassDefFoundError error) {
			return SysErrLogger.info(name);
		}
	}

	public static LogStream debug() {
		String name = getCallerName();
		try {
			return Slf4JLogger.debug(name);
		}
		catch(NoClassDefFoundError error) {
			return SysErrLogger.debug(name);
		}
	}
	
	public static LogStream warn() {
		String name = getCallerName();
		try {
			return Slf4JLogger.warn(name);
		}
		catch(NoClassDefFoundError error) {
			return SysErrLogger.warn(name);
		}		
	}

	public static LogStream error() {
		String name = getCallerName();
		try {
			return Slf4JLogger.error(name);
		}
		catch(NoClassDefFoundError error) {
			return SysErrLogger.error(name);
		}		
	}
	
	
	public abstract void log(String message);

	public abstract void log(String message, Throwable error);
	
}
