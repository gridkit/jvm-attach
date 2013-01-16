package org.gridkit.lab.jvm.attach;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;

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
