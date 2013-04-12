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
public class JavaProcessId {

	private final long pid;
	private final String description;
	
	protected JavaProcessId(long pid, String description) {
		this.pid = pid;
		this.description = description;
	}

	public long getPID() {
		return pid;
	}
	
	public String getDescription() {
		return description == null ? "" : description;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (pid ^ (pid >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		JavaProcessId other = (JavaProcessId) obj;
		if (pid != other.pid)
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return pid + (description == null ? "" : (" " + description)); 
	}
	
}
