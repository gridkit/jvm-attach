package org.gridkit.lab.jvm.attach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HeapHisto {

	private Map<String, Bucket> histo = new LinkedHashMap<String, Bucket>();
	
	public static HeapHisto parse(Iterable<String> text) {
	    HeapHisto histo = new HeapHisto();
	    for(String line: text) {
	        String[] split = line.trim().split("\\s+");
	        if (split.length == 4 && split[0].endsWith(":")) {
	            Bucket b = new Bucket();
	            b.num = Integer.parseInt(split[0].substring(0, split[0].length() - 1));
	            b.instances = Long.parseLong(split[1]);
	            b.bytes = Long.parseLong(split[2]);
	            b.className = split[3];
	            histo.histo.put(b.className, b);
	        }
	    }
	    return histo;
	}
	
	public static HeapHisto subtract(HeapHisto a, HeapHisto b) {
		List<Bucket> buckets = new ArrayList<Bucket>();
		for(Bucket b1: a.getBuckets()) {
			Bucket b2 = b.get(b1.className);
			
			Bucket nb = new Bucket();
			nb.className = b1.className;
			nb.instances = b1.instances;
			nb.bytes = b1.bytes;
			
			if (b2 != null) {
				nb.instances -= b2.instances;
				nb.bytes -= b2.bytes;
			}
			if (nb.bytes != 0 || nb.instances != 0) {
				buckets.add(nb);
			}
		}
		for(Bucket b2: b.getBuckets()) {
			if (a.get(b2.className) == null) {
				Bucket nb = new Bucket();
				nb.className = b2.className;
				nb.instances = -b2.instances;
				nb.bytes = -b2.bytes;
				buckets.add(nb);
			}
		}
		
		Collections.sort(buckets, new SizeComparator());
		for(int i = 0; i != buckets.size(); ++i) {
			buckets.get(i).num = i + 1;
		}
		
		HeapHisto histo = new HeapHisto();
		for(Bucket bb: buckets) {
			histo.histo.put(bb.className, bb);
		}
		
		return histo;
	}
	
	public List<Bucket> getBuckets() {
	    List<Bucket> result = new ArrayList<Bucket>(histo.values());
	    Collections.sort(result, new SizeComparator());
	    return result;
	}
	
	public Bucket get(String classname) {
	    return histo.get(classname);
	}
		
	public long totalInstances() {
	    long sum = 0;
	    for(Bucket b: histo.values()) {
	        sum += b.instances;
	    }
	    return sum;
	}

	public long totalBytes() {
	    long sum = 0;
	    for(Bucket b: histo.values()) {
	        sum += b.bytes;
	    }
	    return sum;
	}
	
	public String print() {
		return print(histo.size());
	}
	
	public String print(int top) {
	    StringBuilder sb = new StringBuilder();
	    int n = 0;
	    for(Bucket b: histo.values()) {
	    	if (++n > top) {
	    		break;
	    	}
	        sb.append(b.toString()).append('\n');
	    }
	    sb.append(String.format("Total%14d%15d\n", totalInstances(), totalBytes()));
	    return sb.toString();
	}
	
	public static class Bucket {
		
		int num;
		String className;
		long instances;
		long bytes;
		
		public String toString() {
		    return String.format("%4d:%14d%15d  %s", num, instances, bytes, className);
		}
	}
	
	public static class SizeComparator implements Comparator<Bucket> {

        @Override
        public int compare(Bucket o1, Bucket o2) {
            return Long.signum(o2.bytes - o1.bytes);
        }
	}
}
