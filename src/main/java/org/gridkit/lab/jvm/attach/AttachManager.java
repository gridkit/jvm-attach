package org.gridkit.lab.jvm.attach;

import java.io.File;
import java.io.IOException;
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

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

public class AttachManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachManager.class);

    static {
    	AttachAPI.ensureToolsJar();
    }
    
    private static long ATTACH_TIMEOUT = TimeUnit.MILLISECONDS.toNanos(500);
    private static long VM_LIST_EXPIRY = TimeUnit.SECONDS.toNanos(1);
    private static long VM_PROPS_EXPIRY = TimeUnit.SECONDS.toNanos(1);
    private static long VM_MBEAN_SERVER_EXPIRY = TimeUnit.SECONDS.toNanos(30);

    
    private static AttachManager INSTANCE = new AttachManager();
    
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
    
    private List<VirtualMachineDescriptor> vmList;
    private long vmListEndOfLife;
    
    private Map<Long, Expirable<Properties>> vmPropsCache = new HashMap<Long, AttachManager.Expirable<Properties>>();            
    private Map<Long, Expirable<MBeanServerConnection>> vmMBeanCache = new HashMap<Long, AttachManager.Expirable<MBeanServerConnection>>();

    
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
				LOGGER.debug("Process filtering exception", e.getCause());
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

	synchronized Properties internalGetSystemProperties(long pid) {
		
		Expirable<Properties> proph = vmPropsCache.get(pid);
		if (proph == null || proph.expiryDeadline < System.nanoTime()) {
			Properties props = getSysProps(pid);
			proph = new Expirable<Properties>(System.nanoTime() + VM_PROPS_EXPIRY, props);
			vmPropsCache.put(pid, proph);
		}
		
		return proph.value;
	}

	private MBeanServerConnection getMBeanServer(long pid) {
		VirtualMachine lvm = null;
		try {
			lvm = attach(String.valueOf(pid));
			String uri = attachManagementAgent(lvm);
    		JMXServiceURL jmxurl = new JMXServiceURL(uri);
    		JMXConnector conn = JMXConnectorFactory.connect(jmxurl);
    		MBeanServerConnection mserver = conn.getMBeanServerConnection();
    		return mserver;
			
		} catch (AttachNotSupportedException e) {
			LOGGER.debug("Attach to (" + pid + ") has failed", e);
			return null;
		} catch (IOException e) {
			LOGGER.debug("Attach to (" + pid + ") has failed", e);
			return null;
		} catch (AgentLoadException e) {
			LOGGER.debug("Agent error at (" + pid + ")", e);
			return null;
		} catch (AgentInitializationException e) {
			LOGGER.debug("Agent error at (" + pid + ")", e);
			return null;
		}
		finally {
			if (lvm != null) {
				try {
					lvm.detach();
				} catch (Exception e) {
					// ignore
				}
			}
		}
	}

	private Properties getSysProps(long pid) {		
		VirtualMachine lvm = null;
		try {
			lvm = attach(String.valueOf(pid));
			return lvm.getSystemProperties();
		} catch (AttachNotSupportedException e) {
			LOGGER.debug("Attach to (" + pid + ") has failed", e);
			return new Properties();
		} catch (IOException e) {
			LOGGER.debug("Attach to (" + pid + ") has failed", e);
			return new Properties();
		}
		finally {
			if (lvm != null) {
				try {
					lvm.detach();
				} catch (Exception e) {
					// ignore
				}
			}
		}
	}

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
	
	private static VirtualMachine attach(final String id)	throws AttachNotSupportedException, IOException {
		FutureTask<VirtualMachine> vmf = new FutureTask<VirtualMachine>(new Callable<VirtualMachine>() {
			@Override
			public VirtualMachine call() throws Exception {
				return VirtualMachine.attach(id);
			}
		});
		Thread attacher = new Thread(vmf);
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
		public MBeanServerConnection getMBeans() {
			return internalGetJmxConnection(pid);
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
}
