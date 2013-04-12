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
class SysErrLogger {

	private static LogStream SYS_ERR = new LogStream() {
		@Override
		public void log(String message) {
			System.err.println(message);
		}
		
		@Override
		public void log(String message, Throwable error) {
			System.err.println(message);
			error.printStackTrace();
		}
	};
	
	private static LogStream NULL = new LogStream() {
		@Override
		public void log(String message) {
			// do nothing
		}
		
		@Override
		public void log(String message, Throwable error) {
			// do nothing
		}
	};
	
	public static LogStream debug(String name) {
		return NULL;
	}

	public static LogStream info(String name) {
		return NULL;
	}

	public static LogStream warn(String name) {
		return SYS_ERR;
	}

	public static LogStream error(String name) {
		return SYS_ERR;
	}
}
