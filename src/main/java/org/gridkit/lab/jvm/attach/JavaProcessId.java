package org.gridkit.lab.jvm.attach;

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
