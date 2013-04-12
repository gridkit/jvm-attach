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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
class Slf4JLogger {

	public static LogStream error(final String name) {
		return new LogStream() {
			
			Logger logger = LoggerFactory.getLogger(name);
			
			@Override
			public void log(String message) {
				logger.error(message);
			}

			@Override
			public void log(String message, Throwable error) {
				logger.error(message, error);
			}			
		};
	}

	public static LogStream warn(final String name) {
		return new LogStream() {
			
			Logger logger = LoggerFactory.getLogger(name);
			
			@Override
			public void log(String message) {
				logger.warn(message);
			}
			
			@Override
			public void log(String message, Throwable error) {
				logger.warn(message, error);
			}			
		};
	}

	public static LogStream info(final String name) {
		return new LogStream() {
			
			Logger logger = LoggerFactory.getLogger(name);
			
			@Override
			public void log(String message) {
				logger.info(message);
			}
			
			@Override
			public void log(String message, Throwable error) {
				logger.info(message, error);
			}			
		};
	}

	public static LogStream debug(final String name) {
		return new LogStream() {
			
			Logger logger = LoggerFactory.getLogger(name);
			
			@Override
			public void log(String message) {
				logger.debug(message);
			}
			
			@Override
			public void log(String message, Throwable error) {
				logger.debug(message, error);
			}			
		};
	}
}
