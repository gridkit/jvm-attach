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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import sun.tools.attach.HotSpotVirtualMachine;

/**
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
public class AttachManager {

    private static final LogStream LOG_WARN = LogStream.warn();
    private static final LogStream LOG_INFO = LogStream.info();
    private static final LogStream LOG_DEBUG = LogStream.debug();

    private static long ATTACH_TIMEOUT = TimeUnit.MILLISECONDS.toNanos(500);
    private static long VM_LIST_EXPIRY = TimeUnit.SECONDS.toNanos(1);
    private static long VM_PROPS_EXPIRY = TimeUnit.SECONDS.toNanos(1);
    private static long VM_MBEAN_SERVER_EXPIRY = TimeUnit.SECONDS.toNanos(30);

    static {
    	AttachAPI.ensureToolsJar();
    }
    
    public static void ensureToolsJar() {
    	// do nothing, just ensure call to static initializer
    }
    
    private static AttachManagerInt INSTANCE = new AttachManagerInt();
    
    private static ExecutorService threadPool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 500, TimeUnit.MILLISECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
    	int counter = 0;
    	
		@Override
		public synchronized Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName("JvmAttachWorker-" + (counter++));
			return t;
		}
	}); 

    public static JavaProcessDetails getDetails(long pid) {
    	return INSTANCE.internalGetDetails(pid);
    }

    public static JavaProcessDetails getDetails(JavaProcessId jpid) {
    	return getDetails(jpid.getPID());
    }
    
    public static List<JavaProcessId> listJavaProcesses() {
    	return INSTANCE.internalListJavaProcesses();
    }

    public static List<JavaProcessId> listJavaProcesses(JavaProcessMatcher matcher) {
    	return INSTANCE.internalListJavaProcesses(matcher);
    }
    
    public static MBeanServerConnection getJmxConnection(long pid) {
    	return INSTANCE.internalGetJmxConnection(pid);
    }

    public static MBeanServerConnection getJmxConnection(JavaProcessId jpid) {
    	return getJmxConnection(jpid.getPID());
    }
    
    public static void loadAgent(long pid, String agentPath, String agentArgs, long timeoutMs) throws Exception {
        INSTANCE.internalLoadAgent(pid, agentPath, agentArgs, timeoutMs);
    }

    public static List<String> getHeapHisto(long pid, Object[] args, long timeoutMs) throws Exception {
    	return INSTANCE.internalHeapHisto(pid, args, timeoutMs);
    }

    /**
     * Sends 'heapdump' command.
     * 
     * @return JVM diagnostic output
     */
    public static String getHeapDump(long pid, Object[] args, long timeoutMs) throws Exception {
        return INSTANCE.internalHeapDump(pid, args, timeoutMs);
    }

    public static void getThreadDump(long pid, Object[] args, Appendable output, long timeoutMs) throws Exception {
        INSTANCE.internalThreadDump(pid, args, output, timeoutMs);
    }

    private static void readString(InputStream stream, Appendable target) throws IOException {    	
		try {
			if (target == null) {
				return;
			}
			Reader r = new InputStreamReader(stream);
			CharBuffer cb = CharBuffer.allocate(16 << 10);
			while(true) {
				int m = r.read(cb.array());
				if (m < 0) {
					break;
				}
				cb.limit(m);
				target.append(cb);
				cb.clear();
			}
		}
		finally {
			try {
				stream.close();
			}
			catch(IOException e) {
				// ignore
			}
		}    	
    }
    
	private static void copy(InputStream in, OutputStream out) throws IOException {
		try {
			byte[] buf = new byte[1 << 12];
			while(true) {
				int n = in.read(buf);
				if(n >= 0) {
					out.write(buf, 0, n);
				}
				else {
					break;
				}
			}
		} finally {
			try {
				in.close();
			}
			catch(Exception e) {
				// ignore
			}
		}
	}	
    
    static class AttachManagerInt {    	
    	
	    private List<VirtualMachineDescriptor> vmList;
	    private long vmListEndOfLife;
	    
	    private Map<Long, Expirable<Properties>> vmPropsCache = new HashMap<Long, Expirable<Properties>>();            
	    private Map<Long, Expirable<MBeanServerConnection>> vmMBeanCache = new HashMap<Long, Expirable<MBeanServerConnection>>();
	    
	    private Map<Long, AttachRequest> attachQueue = new HashMap<Long, AttachRequest>();
	    
	    JavaProcessDetails internalGetDetails(long pid) {
	    	String name = getProcesssName(pid);
			return new ProcessDetails(pid, name);
		}
	
		private String getProcesssName(long pid) {
			List<VirtualMachineDescriptor> vms = getVmList();
			for(VirtualMachineDescriptor vm: vms) {
				if (vm.id().equals(String.valueOf(pid))) {
					return vm.displayName();
				}
			}
			return "";
		}
	
		private synchronized List<VirtualMachineDescriptor> getVmList() {
			if (vmList == null || vmListEndOfLife < System.nanoTime()) {
				vmList = VirtualMachine.list();
				vmListEndOfLife = System.nanoTime() + VM_LIST_EXPIRY;
			}
			return vmList;
		}
	
		List<JavaProcessId> internalListJavaProcesses() {
			List<VirtualMachineDescriptor> vms = getVmList();
			List<JavaProcessId> result = refine(vms);
			return result;
		}
	
		private List<JavaProcessId> refine(List<VirtualMachineDescriptor> vms) {
			JavaProcessId[] jpids = new JavaProcessId[vms.size()];
			for(int i = 0 ; i != jpids.length; ++i) {
				VirtualMachineDescriptor vm = vms.get(i);
				jpids[i] = new JavaProcessId(Long.parseLong(vm.id()), vm.displayName());
			}
			List<JavaProcessId> result = Arrays.asList(jpids);
			return result;
		}
	
		List<JavaProcessId> internalListJavaProcesses(final JavaProcessMatcher matcher) {
			
			List<Future<JavaProcessId>> futures = new ArrayList<Future<JavaProcessId>>();
			List<VirtualMachineDescriptor> vms = getVmList();
			for(final VirtualMachineDescriptor vm: vms) {
				futures.add(threadPool.submit(new Callable<JavaProcessId>() {
	
					@Override
					public JavaProcessId call() throws Exception {
						ProcessDetails pd = new ProcessDetails(Long.parseLong(vm.id()), vm.displayName());
						if (matcher.evaluate(pd)) {
							return new JavaProcessId(pd.pid, pd.name);
						}
						else {
							return null;
						}
					}
					
				}));			
			}
			
			List<JavaProcessId> result = new ArrayList<JavaProcessId>();
			for(Future<JavaProcessId> fp: futures) {
				try {
					JavaProcessId p = fp.get();
					if (p != null) {
						result.add(p);
					}
				}
				catch(InterruptedException e) {
					Thread.interrupted();
					return Collections.emptyList();
				}
				catch(ExecutionException e) {
					LOG_DEBUG.log("Process filtering exception", e.getCause());
				}
			}
			
			return result;
		}
	
		synchronized MBeanServerConnection internalGetJmxConnection(long pid) {
			
			Expirable<MBeanServerConnection> mbh = vmMBeanCache.get(pid);
			if (mbh == null || mbh.expiryDeadline < System.nanoTime()) {
				MBeanServerConnection mserver = getMBeanServer(pid);
				mbh = new Expirable<MBeanServerConnection>(System.nanoTime() + VM_MBEAN_SERVER_EXPIRY, mserver);
				vmMBeanCache.put(pid, mbh);
			}
			
			return mbh.value;
		}
	
		Properties internalGetSystemProperties(long pid) {
			
			Expirable<Properties> proph;
			synchronized(this) {
				proph = vmPropsCache.get(pid);
			}
			if (proph == null || proph.expiryDeadline < System.nanoTime()) {
				Properties props = getSysProps(pid);
				proph = new Expirable<Properties>(System.nanoTime() + VM_PROPS_EXPIRY, props);
				synchronized(this) {
					vmPropsCache.put(pid, proph);
				}
			}
				
			return proph.value;
		}
		
		Properties internalGetAgentProperties(long pid) {
		    return getAgentProps(pid);
		}

        void internalLoadAgent(long pid, String agentPath, String agentArgs, long timeoutMs) throws Exception {
            try {
                attachAndPerform(pid, new LoadAgent(agentPath, agentArgs), TimeUnit.MILLISECONDS.toNanos(timeoutMs));
            }  catch (ExecutionException e) {
        		if (isAttachException(e.getCause())) {
        			throw new Exception(e.getCause().toString());
        		} else {
        			if (e.getCause() instanceof Exception) {
        				throw (Exception)e.getCause();
        			}
        			else {
        				throw e;
        			}
        		}
            } 
		}

        List<String> internalHeapHisto(long pid, Object[] args, long timeoutMs) throws Exception {
        	try {
        		return attachAndPerform(pid, new HeapHisto(args), TimeUnit.MILLISECONDS.toNanos(timeoutMs));
        	}  catch (ExecutionException e) {
        		if (isAttachException(e.getCause())) {
        			throw new Exception(e.getCause().toString());
        		} else {
        			if (e.getCause() instanceof Exception) {
        				throw (Exception)e.getCause();
        			}
        			else {
        				throw e;
        			}
        		}
        	} 
        }

        String internalHeapDump(long pid, Object[] args, long timeoutMs) throws Exception {
            try {
                return attachAndPerform(pid, new HeapDump(args), TimeUnit.MILLISECONDS.toNanos(timeoutMs));
            }  catch (ExecutionException e) {
                if (isAttachException(e.getCause())) {
                    throw new Exception(e.getCause().toString());
                } else {
                    if (e.getCause() instanceof Exception) {
                        throw (Exception)e.getCause();
                    }
                    else {
                        throw e;
                    }
                }
            } 
        }        
        
        String internalPrintFlag(long pid, String flag, long timeoutMs) throws Exception {
        	try {
        		return attachAndPerform(pid, new PrintVmFlag(flag), TimeUnit.MILLISECONDS.toNanos(timeoutMs));
        	}  catch (ExecutionException e) {
        		if (isAttachException(e.getCause())) {
        			throw new Exception(e.getCause().toString());
        		} else {
        			if (e.getCause() instanceof Exception) {
        				throw (Exception)e.getCause();
        			}
        			else {
        				throw e;
        			}
        		}
        	} 
        }

        void internalJCmd(long pid, String command, Appendable output, long timeoutMs) throws Exception {
        	try {
        		attachAndPerform(pid, new JCmdCommand(command, output), TimeUnit.MILLISECONDS.toNanos(timeoutMs));
        	}  catch (ExecutionException e) {
        		if (isAttachException(e.getCause())) {
        			throw new Exception(e.getCause().toString());
        		} else {
        			if (e.getCause() instanceof Exception) {
        				throw (Exception)e.getCause();
        			}
        			else {
        				throw e;
        			}
        		}
        	} 
        }
	
        String internalSendAttachCommand(long pid, String command, Object[] args, OutputStream output, long timeoutMs) throws Exception {
        	try {
        		StringBuilder sb = new StringBuilder();
        		attachAndPerform(pid, new GenericCommand(command, args, output), TimeUnit.MILLISECONDS.toNanos(timeoutMs));
        		return sb.toString();
        	}  catch (ExecutionException e) {
        		if (isAttachException(e.getCause())) {
        			throw new Exception(e.getCause().toString());
        		} else {
        			if (e.getCause() instanceof Exception) {
        				throw (Exception)e.getCause();
        			}
        			else {
        				throw e;
        			}
        		}
        	} 
        }
        
        
        void internalThreadDump(long pid, Object[] args, Appendable output, long timeoutMs) throws Exception {
            try {
                attachAndPerform(pid, new ThreadDump(args, output), TimeUnit.MILLISECONDS.toNanos(timeoutMs));
            }  catch (ExecutionException e) {
                if (isAttachException(e.getCause())) {
                    throw new Exception(e.getCause().toString());
                } else {
                    if (e.getCause() instanceof Exception) {
                        throw (Exception)e.getCause();
                    }
                    else {
                        throw e;
                    }
                }
            }             
        }
        
        private boolean isAttachException(Throwable e) {
        	return e.getClass().getName().startsWith("com.sun.tools.attach.");
        }
        
		private MBeanServerConnection getMBeanServer(long pid) {
			try {
				String uri;
				try {
					uri = attachAndPerform(pid, new GetManagementAgent(), ATTACH_TIMEOUT);
				} catch (InterruptedException e) {
					LOG_WARN.log("Cannot connect to JVM (" + pid + ") - interrupted");
					return null;
				} catch (ExecutionException e) {
					Throwable cause = e.getCause();
					if (cause instanceof AgentLoadException || cause instanceof AgentInitializationException) {
						LOG_DEBUG.log("Cannot connect to JVM (" + pid + "). Agent error: " + e.toString());
						return null;
					}
					else {
						LOG_DEBUG.log("Cannot connect to JVM (" + pid + ") - " + e.toString());
						return null;
					}
				} catch (TimeoutException e) {
					LOG_DEBUG.log("Cannot connect to JVM (" + pid + ") - timeout");
					return null;
				}
	    		JMXServiceURL jmxurl = new JMXServiceURL(uri);
	    		JMXConnector conn = JMXConnectorFactory.connect(jmxurl);
	    		MBeanServerConnection mserver = conn.getMBeanServerConnection();
	    		return mserver;
				
			} catch (Exception e) {
				LOG_DEBUG.log("Cannot connect to JVM (" + pid + ") - " + e.toString());
				return null;
			}
		}
	
		private Properties getSysProps(long pid) {
			try {
				return attachAndPerform(pid, new GetVmSysProps(), ATTACH_TIMEOUT);
			} catch (InterruptedException e) {
				LOG_WARN.log("Failed to read system properties, JVM pid: " + pid + ", interrupted");
				return new Properties();
			} catch (ExecutionException e) {
				LOG_INFO.log("Failed to read system properties, JVM pid: " + pid + ", error: " + e.getCause().toString());
				return new Properties();
			} catch (TimeoutException e) {
				LOG_INFO.log("Failed to read system properties, JVM pid: " + pid + ", read timeout");
				return new Properties();
			}
		}

        private Properties getAgentProps(long pid) {
            try {
                return attachAndPerform(pid, new GetVmAgentProps(), ATTACH_TIMEOUT);
            } catch (InterruptedException e) {
                LOG_WARN.log("Failed to read agent properties, JVM pid: " + pid + ", interrupted");
                return new Properties();
            } catch (ExecutionException e) {
                LOG_INFO.log("Failed to read agent properties, JVM pid: " + pid + ", error: " + e.getCause().toString());
                return new Properties();
            } catch (TimeoutException e) {
                LOG_INFO.log("Failed to read agent properties, JVM pid: " + pid + ", read timeout");
                return new Properties();
            }
        }
	
		@SuppressWarnings("unused")
		private static String attachManagementAgent(VirtualMachine vm) throws IOException, AgentLoadException, AgentInitializationException
		{
	     	Properties localProperties = vm.getAgentProperties();
	     	if (localProperties.containsKey("com.sun.management.jmxremote.localConnectorAddress")) {
	     		return ((String)localProperties.get("com.sun.management.jmxremote.localConnectorAddress"));
	     	}
			
			String jhome = vm.getSystemProperties().getProperty("java.home");
		    Object localObject = jhome + File.separator + "jre" + File.separator + "lib" + File.separator + "management-agent.jar";
		    File localFile = new File((String)localObject);
		    
		    if (!(localFile.exists())) {
		       localObject = jhome + File.separator + "lib" + File.separator + "management-agent.jar";
		 
		       localFile = new File((String)localObject);
		       if (!(localFile.exists())) {
		    	   throw new IOException("Management agent not found"); 
		       }
		    }
		 
	     	localObject = localFile.getCanonicalPath();     	
	 		vm.loadAgent((String)localObject, "com.sun.management.jmxremote");
	 
	     	localProperties = vm.getAgentProperties();
	     	return ((String)localProperties.get("com.sun.management.jmxremote.localConnectorAddress"));
	   	}
		
		private <V> V attachAndPerform(long pid, VMAction<V> action, long timeout) throws InterruptedException, ExecutionException, TimeoutException {
			VMTask<V> task = new VMTask<V>(action);
			boolean spawnThread = false;
			AttachRequest ar;
			synchronized(attachQueue) {
				ar = attachQueue.get(pid);
				if (ar != null) {
					ar.tasks.add(task);
				}
				else {
					ar = new AttachRequest(pid);
					ar.tasks.add(task);
					spawnThread = true;
					attachQueue.put(pid, ar);
				}				
			}
			if (spawnThread) {
				Thread attacher = new Thread(ar);
				attacher.setName("AttachToJVM-" + pid);
				attacher.setDaemon(true);
				attacher.start();				
			}

			try {
				V result = task.box.get(timeout, TimeUnit.MILLISECONDS);
				return result;
			}
			finally {
				task.box.cancel(false);
			}
		}
		
		@SuppressWarnings("unused")
		private static VirtualMachine attachToJvm(final String id)	throws AttachNotSupportedException, IOException {
			FutureTask<VirtualMachine> vmf = new FutureTask<VirtualMachine>(new Callable<VirtualMachine>() {
				@Override
				public VirtualMachine call() throws Exception {
					return VirtualMachine.attach(id);
				}
			});
			Thread attacher = new Thread(vmf);
			attacher.setName("AttachManager::attachToJvm(" + id + ")");
			attacher.setDaemon(true);
			attacher.start();
			try {
				return vmf.get(ATTACH_TIMEOUT, TimeUnit.NANOSECONDS);
			} catch (Exception e) {
				IOException er;
				if (e instanceof ExecutionException) {
					er = new IOException(e.getCause());
				}
				else {
					er = new IOException(e);
				}
				if (attacher.isAlive()) {
					attacher.interrupt();
				}
				throw er;
			}
		}    

		@SuppressWarnings("unused")
		private static Properties getSysPropsAsync(final String id) {
			FutureTask<Properties> vmf = new FutureTask<Properties>(new Callable<Properties>() {
				@Override
				public Properties call() throws Exception {
					VirtualMachine vm = null;
					try {
						vm = VirtualMachine.attach(id);
						return vm.getSystemProperties();
					} finally {
						if (vm != null) {
							vm.detach();
						}
					}
				}
			});
			Thread attacher = new Thread(vmf);
			attacher.setName("AttachManager::getSysPropsAsync(" + id + ")");
			attacher.setDaemon(true);
			attacher.start();
			try {
				return vmf.get(ATTACH_TIMEOUT, TimeUnit.NANOSECONDS);
			} catch (Exception e) {
				Throwable x = e;
				if (e instanceof ExecutionException) {
					x = e.getCause();
				}
				if (attacher.isAlive()) {
					attacher.interrupt();
				}
				LOG_INFO.log("Attach to (" + id + ") has failed: " + x.toString());
				LOG_DEBUG.log("Attach to (" + id + ") has failed", x);

				return new Properties();
			}
		}    
		
		private class ProcessDetails implements JavaProcessDetails {
			
			private final long pid;
			private final String name;
			
			public ProcessDetails(long pid, String name) {
				this.pid = pid;
				this.name = name;
			}
	
			@Override
			public long getPid() {
				return pid;
			}
			
			@Override
			public String getDescription() {
				return name;
			}
			
			@Override
			public JavaProcessId getJavaProcId() {
				return new JavaProcessId(pid, name);
			}
	
			@Override
			public Properties getSystemProperties() {
				return internalGetSystemProperties(pid);
			}
			
            @Override
            public Properties getAgentProperties() {
                return internalGetAgentProperties(pid);
            }
			
            /**
             * Queries JVM for internal flag. Will return <code>null</code> if command is not supported.
             */
			@Override
			public String getVmFlag(String flag) {
				try {
					return internalPrintFlag(pid, flag, 5 * ATTACH_TIMEOUT);
				} catch (Exception e) {
					return null;
				}
			}

			@Override
			public MBeanServerConnection getMBeans() {
				return internalGetJmxConnection(pid);
			}

			@Override
			public void jcmd(String command, Appendable result) {
				try {
					internalJCmd(pid, command, result, ATTACH_TIMEOUT);
				}
				catch(RuntimeException e) {
					throw e;
				}
				catch(Exception e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void sendAttachCommand(String command, Object[] args, OutputStream output, long timeoutMS) {
				try {
					internalSendAttachCommand(pid, command, args, output, timeoutMS);
				}
				catch(RuntimeException e) {
					throw e;
				}
				catch(Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		
		private static class Expirable<V> {
	    	
	    	long expiryDeadline;
	    	V value;
			
	    	public Expirable(long expiryDeadline, V value) {
				this.expiryDeadline = expiryDeadline;
				this.value = value;
			}
	    }
		
		private class AttachRequest implements Runnable {
			
			final long pid;			
			final List<VMTask<?>> tasks;
			
			public AttachRequest(long pid) {
				this.pid = pid;
				this.tasks = new ArrayList<VMTask<?>>();
			}

			@Override
			public void run() {
				VirtualMachine vm;
				try {
					try {
						vm = VirtualMachine.attach(String.valueOf(pid));
					}
					catch(IOException e) {
						LogStream.debug().log("Attach attempt failed, would retry", e);
						// second try
						vm = VirtualMachine.attach(String.valueOf(pid));
					}
					dispatch(vm);
				} catch (Throwable e) {
					fail(e);
					return;
				}
				try {
					vm.detach();
				} catch (IOException e) {
					// ignore
				}
			}

			private void dispatch(VirtualMachine vm) {
				while(true) {
					VMTask<?> task;
					synchronized(attachQueue) {
						if (tasks.isEmpty()) {
							attachQueue.remove(pid);
							return;
						}
						else {
							task = tasks.remove(0);
						}
					}
					task.perform(vm);
				}
			}

			private void fail(Throwable e) {
				LOG_INFO.log("Attach to (" + pid + ") has failed: " + e.toString());
				LOG_DEBUG.log("Attach to (" + pid + ") has failed", e);
				while(true) {
					VMTask<?> task;
					synchronized(attachQueue) {
						if (tasks.isEmpty()) {
							attachQueue.remove(pid);
							return;
						}
						else {
							task = tasks.remove(0);
						}
					}
					task.fail(e);
				}
			}
		}
		
		private static class FutureBox<V> extends FutureTask<V> {

			private FutureBox(Callable<V> callable) {
				super(callable);
			}

			@Override
			public void setException(Throwable t) {
				super.setException(t);
			}
		}
		
		private static class VMTask<V> {
			
			VirtualMachine vm;
			VMAction<V> action;
			FutureBox<V> box;
			
			public VMTask(VMAction<V> action) {
				this.action = action;
				this.box = new FutureBox<V>(new Callable<V>() {
					@Override
					public V call() throws Exception {
						return VMTask.this.action.perform(new VMWarpper(vm));
					}
				});
			}
			
			public void perform(VirtualMachine vm) {
				this.vm = vm;
				box.run();
				this.vm = null;
			}			

			public void fail(Throwable e) {
				box.setException(e);
			}			
		}
		
		private static interface VMAction<V> {

			public V perform(VMWarpper vm) throws Exception;
		}
		
		private static class GetVmSysProps implements VMAction<Properties> {

			@Override
			public Properties perform(VMWarpper vm) throws IOException {
				return vm.getSystemProperties();
			}
		}
		
        private static class GetVmAgentProps implements VMAction<Properties> {

            @Override
            public Properties perform(VMWarpper vm) throws IOException {
                return vm.getAgentProperties();
            }
        }

        private static class PrintVmFlag implements VMAction<String> {
        	
        	private String flag;
        	
        	public PrintVmFlag(String flag) {
				this.flag = flag;
			}

			@Override
        	public String perform(VMWarpper vm) throws IOException {
        		try {
					InputStream is = vm.printFlag(flag);
					BufferedReader br = new BufferedReader(new InputStreamReader(is));
					return br.readLine();
				} catch (Exception e) {
					return null;
				}
        	}
        }

		private static class GetManagementAgent implements VMAction<String> {
			
			@Override
			public String perform(VMWarpper vm) throws IOException, AgentLoadException, AgentInitializationException {
		     	Properties localProperties = vm.getAgentProperties();
		     	if (localProperties.containsKey("com.sun.management.jmxremote.localConnectorAddress")) {
		     		String jmxuri = (String)localProperties.get("com.sun.management.jmxremote.localConnectorAddress");
		     		LOG_DEBUG.log("JMX agent already running: " + jmxuri);
		     		return jmxuri;
		     	}
				
		     	if (vm.isJCmdSupported()) {
		     		LOG_DEBUG.log("[" + vm.id() + "] try to start JMX via jcmd");
		     		
		     		try {
						StringBuilder sb = new StringBuilder();
						readString(vm.execJCmd("ManagementAgent.start_local"), sb);
						if (sb.length() > 0) {
							LOG_DEBUG.log("[" + vm.id() + "] ManagementAgent.start_local -> " + sb.toString());
						}
					} catch (Exception e) {
						LOG_DEBUG.log("[" + vm.id() + "] Failed to exec ManagementAgent.start_local: " + e.toString());
					}		     		
		     	}
		     	else {
		     		LOG_DEBUG.log("[" + vm.id() + "] try to starn JMX via 'management-agent.jar'");
		     	
					String jhome = vm.getSystemProperties().getProperty("java.home");
				    Object localObject = jhome + File.separator + "jre" + File.separator + "lib" + File.separator + "management-agent.jar";
				    File localFile = new File((String)localObject);
				    
				    if (!(localFile.exists())) {
				       localObject = jhome + File.separator + "lib" + File.separator + "management-agent.jar";
				 
				       localFile = new File((String)localObject);
				       if (!(localFile.exists())) {
				    	   LOG_DEBUG.log("Failed to find 'management-agent.jar' cannot start JMX agent");
				    	   throw new IOException("Failed to find 'management-agent.jar' cannot start JMX agent"); 
				       }
				    }
				 
			     	localObject = localFile.getCanonicalPath();     	
			     	LOG_DEBUG.log("[" + vm.id() + "] load agent form " + localObject);
			 		vm.loadAgent((String)localObject, "com.sun.management.jmxremote");
		     	}
		 
		     	localProperties = vm.getAgentProperties();
		     	return ((String)localProperties.get("com.sun.management.jmxremote.localConnectorAddress"));
			}
		}
		
		private static class LoadAgent implements VMAction<Void> {
		    private final String agentPath;
		    private final String agentArgs; 

            public LoadAgent(String agentPath, String agentArgs) {
                this.agentPath = agentPath;
                this.agentArgs = agentArgs;
            }

            @Override
            public Void perform(VMWarpper vm) throws Exception {
                vm.loadAgent(agentPath, agentArgs);
                return null;
            }
		}

		private static class HeapHisto implements VMAction<List<String>> {

			private final Object[] args; 
			
			public HeapHisto(Object[] args) {
				this.args = args;
			}
			
			@Override
			public List<String> perform(VMWarpper vm) throws Exception {
				InputStream is = vm.heapHisto(args);
				List<String> result = new ArrayList<String>();
				BufferedReader reader = new BufferedReader(new InputStreamReader(is));
				String line;
				while(null != (line = reader.readLine())) {
					result.add(line);
				}
				return result;
			}
		}

		private static class HeapDump implements VMAction<String> {
		    
		    private final Object[] args; 
		    
		    public HeapDump(Object[] args) {
		        this.args = args;
		    }
		    
		    @Override
		    public String perform(VMWarpper vm) throws Exception {
		        StringWriter sw = new StringWriter();
		        readString(vm.dumpHeap(args), sw);
		        return sw.toString();
		    }
		}

        private static class ThreadDump implements VMAction<Void> {
            
            private final Object[] args; 
            private final Appendable writer;
            
            public ThreadDump(Object[] args, Appendable writer) {
                this.args = args;
                this.writer = writer;
            }
            
            @Override
            public Void perform(VMWarpper vm) throws Exception {
                readString(vm.remoteDataDump(args), writer);
                return null;
            }
        }

        private static class JCmdCommand implements VMAction<Void> {
        	
        	private final String command; 
        	private final Appendable writer;
        	
        	public JCmdCommand(String command, Appendable writer) {
        		this.command = command;
        		this.writer = writer;
        	}
        	
        	@Override
        	public Void perform(VMWarpper vm) throws Exception {
        		if (!vm.isJCmdSupported()) {
        			throw new NoSuchMethodException("jcmd is not supported for this VM");
        		}
        		readString(vm.execJCmd(command), writer);
        		return null;
        	}
        }
        
        private static class GenericCommand implements VMAction<Void> {
        	
        	private final String command; 
        	private final Object[] args; 
        	private final OutputStream output;
        	
        	public GenericCommand(String command, Object[] args, OutputStream output) {
        		this.command = command;
        		this.args = args;
        		this.output = output;
        	}
        	
        	@Override
        	public Void perform(VMWarpper vm) throws Exception {
        		InputStream is = vm.execCommand(command, args);
        		if (output != null) {
        			copy(is, output);
        		}
        		else {
        			try {
						is.close();
					} catch (Exception e) {
						// ignore
					}
        		}
        		return null;
        	}
        }
        
        public static class VMWarpper {
        	
        	private final VirtualMachine vm;
        	
        	public VMWarpper(VirtualMachine vm) {
				this.vm = vm;
			}

//			public VirtualMachine unwarp() {
//        		return vm;
//        	}
//
			public String id() {
				return vm.id();
			}
        	
			public void loadAgent(String agent, String args) throws AgentLoadException, AgentInitializationException, IOException {
				vm.loadAgent(agent, args);
			}

			public Properties getSystemProperties() throws IOException {
				return vm.getSystemProperties();
			}

			public Properties getAgentProperties() throws IOException {
				return vm.getAgentProperties();
			}
			
			public InputStream remoteDataDump(Object... args) throws IOException {
				return ((HotSpotVirtualMachine)vm).remoteDataDump(args);
			}

			public InputStream dumpHeap(Object... args) throws IOException {
				return ((HotSpotVirtualMachine)vm).dumpHeap(args);
			}

			public InputStream heapHisto(Object... args) throws IOException {
				return ((HotSpotVirtualMachine)vm).heapHisto(args);
			}

			public InputStream setFlag(String name, String value) throws IOException {
				return ((HotSpotVirtualMachine)vm).setFlag(name, value);
			}

			public InputStream printFlag(String name) throws IOException {
				return ((HotSpotVirtualMachine)vm).printFlag(name);
			}

			public boolean isJCmdSupported() {
				return getExecuteJCmdMethod() != null;
			}
			
			public InputStream execJCmd(String command) throws Exception {
				try {
					return (InputStream) getExecuteJCmdMethod().invoke(vm, command);
				} catch (InvocationTargetException e) {
					if (e.getTargetException() instanceof Exception) {
						throw (Exception)e.getTargetException();
					}
					else {
						throw new ExecutionException(e.getTargetException()); 
					}
				}
			}

			public InputStream execCommand(String command, Object[] args) throws Exception {
				try {
					return (InputStream) getExecuteCommand().invoke(vm, command, args);
				} catch (InvocationTargetException e) {
					if (e.getTargetException() instanceof Exception) {
						throw (Exception)e.getTargetException();
					}
					else {
						throw new ExecutionException(e.getTargetException()); 
					}
				}
			}

			private Method getExecuteJCmdMethod() {
				Class<?> c = vm.getClass();
				while(c != Object.class) {
					try {
						Method m = c.getDeclaredMethod("executeJCmd", String.class);
						try {
							m.setAccessible(true);
							return m;
						} catch (Exception e) {
							LOG_DEBUG.log("Failed setAccessible on " + c.getSimpleName() + "." + m.getName(), e);
						}
					}
					catch(NoSuchMethodException e) {
					}					
					c = c.getSuperclass();
				}
				return null;
			}

			private Method getExecuteCommand() {
				Class<?> c = vm.getClass();
				while(c != Object.class) {
					try {
						Method m = c.getDeclaredMethod("executeCommand", String.class, Object[].class);//c.getDeclaredMethods();
						try {
							m.setAccessible(true);
							return m;
						} catch (Exception e) {
							LOG_DEBUG.log("Failed setAccessible on " + c.getSimpleName() + "." + m.getName(), e);
						}
					}
					catch(NoSuchMethodException e) {
					}					
					c = c.getSuperclass();
				}
				c = vm.getClass();
				while(c != Object.class) {
					try {
						Method m = c.getDeclaredMethod("execute", String.class, Object[].class);
						try {
							m.setAccessible(true);
							return m;
						} catch (Exception e) {
							LOG_DEBUG.log("Failed setAccessible on " + c.getSimpleName() + "." + m.getName(), e);
						}
					}
					catch(NoSuchMethodException e) {
					}					
					c = c.getSuperclass();
				}
				return null;
			}			
        }
    }
}
