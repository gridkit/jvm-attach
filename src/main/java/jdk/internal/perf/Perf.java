package jdk.internal.perf;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Stub class to support pre and post Java 9 runtimes.
 * This class should never be loaded at runtime.
 * 
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
public final class Perf {

	public static Perf getPerf() {
		throw new UnsupportedOperationException("Stub class");
	}

	public ByteBuffer attach(int paramInt, String paramString) throws IllegalArgumentException, IOException {
		throw new UnsupportedOperationException("Stub class");
	}

	public ByteBuffer attach(String paramString1, int paramInt, String paramString2)
			throws IllegalArgumentException, IOException {
		throw new UnsupportedOperationException("Stub class");
	}

	public ByteBuffer createLong(String paramString, int paramInt1, int paramInt2, long paramLong) {
		throw new UnsupportedOperationException("Stub class");		
	}

	public ByteBuffer createString(String paramString1, int paramInt1, int paramInt2, String paramString2,	int paramInt3) {
		throw new UnsupportedOperationException("Stub class");
	}

	public ByteBuffer createString(String paramString1, int paramInt1, int paramInt2, String paramString2) {
		throw new UnsupportedOperationException("Stub class");
	}

	public ByteBuffer createByteArray(String paramString, int paramInt1, int paramInt2, byte[] paramArrayOfByte, int paramInt3) {
		throw new UnsupportedOperationException("Stub class");
	}
	
	public long highResCounter() {
		throw new UnsupportedOperationException("Stub class");
	}

	public long highResFrequency() {
		throw new UnsupportedOperationException("Stub class");
	}
}