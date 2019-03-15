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

import java.io.PrintStream;

/**
 * Fallback console logging.
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
public class SysLogger {
	
	public static final SysLogStream DEBUG = new SysLogStream(null);
	public static final SysLogStream INFO = new SysLogStream(null);
	public static final SysLogStream WARN = new SysLogStream(System.err);
	public static final SysLogStream ERROR = new SysLogStream(System.err);

	public static class SysLogStream extends LogStream {
		
		private PrintStream target;

		public SysLogStream(PrintStream target) {
			this.target = target;
		}
		
		public void setTarget(PrintStream ps) {
			this.target = ps;
		}

		@Override
		public void log(String message) {
			if (target != null) {
				target.println(message);
			}			
		}

		@Override
		public void log(String message, Throwable error) {
			if (target != null) {
				if (message.length() > 0) {
					target.println(message);
				}
				error.printStackTrace(target);				
			}			
		}
	}
	
	public static LogStream debug(String name) {
		return DEBUG;
	}

	public static LogStream info(String name) {
		return INFO;
	}

	public static LogStream warn(String name) {
		return WARN;
	}

	public static LogStream error(String name) {
		return ERROR;
	}
}
