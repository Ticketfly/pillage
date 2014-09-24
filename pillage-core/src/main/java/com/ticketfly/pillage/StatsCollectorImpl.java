/*
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may
 *  not use this file except in compliance with the License. You may obtain
 *  a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.ticketfly.pillage;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;


/**
 * Attaches to a StatsContainer and reports on all the counters, metrics, gauges, and labels.
 * Each report resets state, so counters are reported as deltas, and metrics distributions are
 * only tracked since the last report.
 */
public class StatsCollectorImpl implements StatsCollector {
    private StatsContainer container;
    private List<StatsReporter> reporters = new ArrayList<StatsReporter>();
    private long lastSnap;
    private long currentSnap;
    private boolean includeJvmStats = false;

    private Map<String, Long> lastCounterMap = new HashMap<String, Long>();
    private Map<String, Long> deltaCounterMap = new HashMap<String, Long>();
    private Map<String, Distribution> lastMetricMap = new HashMap<String, Distribution>();
    private Map<String, Distribution> deltaMetricMap = new HashMap<String, Distribution>();
    private Map<String, Double> lastGaugeMap = new HashMap<String, Double>();
    private Map<String, Double> deltaGaugeMap = new HashMap<String, Double>();
    
    public StatsCollectorImpl(StatsContainer container){
    	this(container, true, false);
    }
    
    public StatsCollectorImpl(StatsContainer container, boolean startClean) {
    	this(container, true, false);
    }
    
    public StatsCollectorImpl(StatsContainer container, boolean startClean, boolean includeJvmStats){
    	this.container = container;
    	this.includeJvmStats = includeJvmStats;
        if (startClean) {
            for (Map.Entry<String, Long> entry : this.container.counters().entrySet()) {
                lastCounterMap.put(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Distribution> entry : this.container.metrics().entrySet()) {
                lastMetricMap.put(entry.getKey(), entry.getValue());
            }
            lastGaugeMap.putAll(this.container.gauges());
        }
    }
       
    /**
     * {@inheritDoc}
     */
    @Override
	public void includeJvmStats(boolean flag) {
		this.includeJvmStats = flag;
		
	}

    /**
     * {@inheritDoc}
     */
	@Override
	public boolean areJvmStatsIncluded() {
		return includeJvmStats;
	}

	/**
     * {@inheritDoc}
     */
    @Override
	public StatsSummary getFullSummary() {
    	StatsSummary summary = container.getSummary();
    	
    	if(!includeJvmStats){
    		return summary;
    	}
    	
    	Map<String, Double> gauges = getJvmStats();
    	gauges.putAll(summary.getGauges());
    	return new StatsSummary(summary.getCounters(), summary.getMetrics(), summary.getLabels(), gauges, summary.getStart(), summary.getEnd());
	}

    /**
     * {@inheritDoc}
     */
	@Override
	public StatsSummary getDeltaSummary() {
		if ( ! includeJvmStats )
			return new StatsSummary(deltaCounterMap, deltaMetricMap, container.labels(), container.gauges() , lastSnap, currentSnap);
		else {
			//Map<String, Double> gauges = getJvmStats();
	    	//gauges.putAll(container.gauges());
	    	return new StatsSummary(deltaCounterMap, deltaMetricMap, container.labels(), deltaGaugeMap , lastSnap, currentSnap);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public StatsSummary collect() {
		triggerCounterSnap();
		triggerGaugeSnap();
		triggerMetricSnap();
		lastSnap = currentSnap;
		currentSnap = System.currentTimeMillis();
		StatsSummary deltas = getDeltaSummary();
		for( StatsReporter reporter : reporters){
			reporter.report(deltas);
		}
		return deltas;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addReporter(StatsReporter reporter) {
		synchronized(this) {
			reporters.add(reporter);
		}		
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeReporter(StatsReporter reporter) {
		synchronized(this){
			reporters.remove(reporter);
		}
	}

	/**
	 * Trigger a collection of the counters and overwrite the last collection.
	 */
    protected void triggerCounterSnap() {
        Map<String, Long> deltas = new HashMap<String, Long>();
        synchronized (this) {

            for (Map.Entry<String, ReportingInstance > instanceEntry : container.getReportingInstances().entrySet()) {
                long lastValue = 0;
                String counterName = instanceEntry.getKey();
                long currentValue = instanceEntry.getValue().count;
                if (lastCounterMap.containsKey(counterName)) {
                    lastValue = lastCounterMap.get(counterName);
                    if (instanceEntry.getValue().mode == ReportingMode.DIFFERENTIAL) {
                        deltas.put(counterName, StatUtils.delta(lastValue, currentValue));
                    } else {
                        deltas.put(counterName, currentValue);
                    }
                }
                lastCounterMap.put(counterName, currentValue);
           }
        }
        deltaCounterMap = deltas;
    }

    /**
     * Trigger a collection of the metrics and overwrite the last collection.
     */
    public void triggerMetricSnap() {
        Map<String, Distribution> deltas = new HashMap<String, Distribution>();
        synchronized (this) {

            for (Map.Entry<String, Distribution> entry : container.metrics().entrySet()) {

                if (lastMetricMap.containsKey(entry.getKey())) {
                    Distribution dist = lastMetricMap.get(entry.getKey());
                    deltas.put(entry.getKey(), entry.getValue().delta(dist));

                } else {
                    deltas.put(entry.getKey(), entry.getValue());
                }
                lastMetricMap.put(entry.getKey(), entry.getValue());
            }
        }

        deltaMetricMap = deltas;
    }
   
    /**
     * Snap gauges into metrics
     */
    public void triggerGaugeSnap(){
        Map<String, Double> deltas = new HashMap<String, Double>();
        synchronized (this) {
        	Map<String, Double> gauges = container.gauges();
            if(includeJvmStats){
            	gauges.putAll( getJvmStats() );
            }
            for (Map.Entry<String, Double> entry : gauges.entrySet()) {
                double lastValue = 0;
                if (lastGaugeMap.containsKey(entry.getKey()))
                	lastValue = lastGaugeMap.get(entry.getKey());
                
                deltas.put(entry.getKey(), StatUtils.delta(lastValue, entry.getValue()));
                lastGaugeMap.put(entry.getKey(), entry.getValue());
            }
        }
        deltaGaugeMap = deltas;
    }
    
    public static final String HEAP_USED           = "jvm.heap.used.mbytes";
    public static final String HEAP_USED_INIT      = "jvm.heap.init.mbytes";
    public static final String HEAP_USED_MAX       = "jvm.heap.used.max.mbytes";
    public static final String HEAP_USED_COMMITTED = "jvm.heap.used.committed.mbytes";
    
    public static final String NONHEAP_USED           = "jvm.nonheap.used.mbytes";
    public static final String NONHEAP_USED_INIT      = "jvm.nonheap.used.init.mbytes";
    public static final String NONHEAP_USED_MAX       = "jvm.nonheap.used.max.mbytes";
    public static final String NONHEAP_USED_COMMITTED = "jvm.nonheap.used.committed.mbytes";
    
    public static final String THREAD_DAEMON_CNT = "jvm.thread.daemon.cnt";
    public static final String THREAD_CNT        = "jvm.thread.cnt";
    public static final String THREAD_PEAK_CNT   = "jvm.thread.peak.cnt";
    public static final String THREAD_STARTED_CNT = "jvm.thread.started.cnt";
    
    public static final String START_TIME = "jvm.start_time.millis";
    public static final String UPTIME     = "jvm.uptime.millis";
    
    public static final String LOADED_CLASS_CNT       = "jvm.classes.loaded.cnt";
    public static final String TOTAL_LOADED_CLASS_CNT = "jvm.classes.loaded.total.cnt";
    public static final String UNLOADED_CLASS_CNT     = "jvm.classes.unloaded.cnt";
    
    public static final String GC = "jvm.gc.";
    public static final String CNT = ".collection.cnt";
    public static final String MILLIS = ".collection.time.millis";
    
    /**
     * Refer to the static final strings for the jvm stats returned.
     * 
     * @return
     */
    public Map<String, Double> getJvmStats(){
    	MemoryMXBean memory        = ManagementFactory.getMemoryMXBean();
    	ThreadMXBean threads       = ManagementFactory.getThreadMXBean();
    	RuntimeMXBean runtime      = ManagementFactory.getRuntimeMXBean();
    	ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
    	Map<String, Double> map    = new HashMap<String, Double>();
    	
    	MemoryUsage heapUsed = memory.getHeapMemoryUsage();
    	map.put(HEAP_USED_INIT, (double) heapUsed.getInit() / (1024 * 1024));
    	map.put(HEAP_USED_MAX, (double) heapUsed.getMax() / (1024 * 1024));
    	map.put(HEAP_USED_COMMITTED, (double) heapUsed.getCommitted() / (1024 * 1024));
    	map.put(HEAP_USED,(double) heapUsed.getUsed() / ( 1024 * 1024 ));
    	
    	MemoryUsage nonheapUsed = memory.getNonHeapMemoryUsage();
    	map.put(NONHEAP_USED_INIT, (double) nonheapUsed.getInit() / ( 1024 * 1024 ) );
    	map.put(NONHEAP_USED_MAX, (double) nonheapUsed.getMax() / ( 1024 * 1024 ) );
    	map.put(NONHEAP_USED_COMMITTED, (double) nonheapUsed.getCommitted() / ( 1024 * 1024 ));
    	map.put(NONHEAP_USED, (double) nonheapUsed.getUsed() / ( 1024 * 1024));
    	
    	// thread stats
    	map.put(THREAD_CNT, (double) threads.getThreadCount());
    	map.put(THREAD_DAEMON_CNT, (double) threads.getDaemonThreadCount());
    	map.put(THREAD_PEAK_CNT, (double) threads.getPeakThreadCount());
    	map.put(THREAD_STARTED_CNT, (double) threads.getTotalStartedThreadCount());
    	
    	// runtime stats
    	map.put(START_TIME, (double) runtime.getStartTime());
    	map.put(UPTIME, (double) runtime.getUptime());
    	
    	// classloading stats
    	map.put(LOADED_CLASS_CNT, (double) classes.getLoadedClassCount());
    	map.put(UNLOADED_CLASS_CNT, (double) classes.getUnloadedClassCount());
    	map.put(TOTAL_LOADED_CLASS_CNT, (double) classes.getTotalLoadedClassCount());
    	
    	// garbage collection stats
    	for(GarbageCollectorMXBean gbean : ManagementFactory.getGarbageCollectorMXBeans()){
    		map.put(GC + gbean.getName().replace(" ", "_") + CNT, (double) gbean.getCollectionCount());
    		map.put(GC + gbean.getName().replace(" ", "_") + MILLIS, (double) gbean.getCollectionTime());
    	}
    	
    	return map;
    }
    

}

