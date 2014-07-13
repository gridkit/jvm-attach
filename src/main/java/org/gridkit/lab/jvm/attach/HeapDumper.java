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

import java.io.File;
import java.io.FileNotFoundException;

/**
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
public class HeapDumper {

	public static String dumpLive(int pid, String targetFile, long timeoutMs) {
		try {
		    File file = new File(targetFile).getCanonicalFile();
		    file.getParentFile().mkdirs();
		    if (!file.getParentFile().isDirectory()) {
		        throw new FileNotFoundException("Cannot create: " + file.getPath());
		    }
		    file.delete();
			String[] plive = { file.getPath(), "-live" };
	        return AttachManager.getHeapDump(pid, plive, timeoutMs);
		}
		catch(RuntimeException e) {
			throw e;			
		}
		catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static String dumpAll(int pid, String targetFile, long timeoutMs) {
        try {
            File file = new File(targetFile).getCanonicalFile();
            file.getParentFile().mkdirs();
            if (!file.getParentFile().isDirectory()) {
                throw new FileNotFoundException("Cannot create: " + file.getPath());
            }
            file.delete();
            String[] plive = { file.getPath()};
            return AttachManager.getHeapDump(pid, plive, timeoutMs);
        }
        catch(RuntimeException e) {
            throw e;            
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
	}
}
