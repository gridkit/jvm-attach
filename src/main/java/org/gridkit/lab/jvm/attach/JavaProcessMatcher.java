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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
public interface JavaProcessMatcher {

	public boolean evaluate(JavaProcessDetails details);
	
	public static class Union implements JavaProcessMatcher, Serializable {
		
		private static final long serialVersionUID = 20121112L;
		
		private final JavaProcessMatcher[] matchers;
		
		public Union(JavaProcessMatcher... matchers) {
			this.matchers = matchers;
			if (matchers.length == 0) {
				throw new IllegalArgumentException("Matcher list is empty");
			}
		}

		public Union(Collection<JavaProcessMatcher> matchers) {
			this(matchers.toArray(new JavaProcessMatcher[0]));
		}

		@Override
		public boolean evaluate(JavaProcessDetails details) {
			for(JavaProcessMatcher matcher: matchers) {
				if (matcher.evaluate(details)) {
					return true;
				}
			}
			return false;
		}
		
		@Override
		public String toString() {
			return "UNION" + Arrays.asList(matchers);
		}
	}
}
