package com.ticketfly.pillage.groovy

import com.ticketfly.pillage.HistogramMetricFactory
import com.ticketfly.pillage.StatsContainerImpl
import com.ticketfly.pillage.StatsSummary
import spock.lang.Specification
import com.ticketfly.pillage.Distribution

class GroovyStatsContainerSpec extends Specification {
    
    def "The time method should return the actual result of the nested closure"() {
        given:
            final String resultString = "the closure's result"
            final String timerName = "test_timer"
            final String expectedMetricName = "${timerName}.millis"
            def container = new GroovyStatsContainer( new StatsContainerImpl(new HistogramMetricFactory()))
        when:
            def result = container.time(timerName) {
                Thread.currentThread().sleep(200)
                return resultString
            }

        then:
            result == resultString
            StatsSummary summary = container.summary
            Distribution distribution = summary.metrics.get(expectedMetricName)
            distribution != null
            distribution.count == 1
            distribution.maximum >= 200
    }

	def "The time method should be added to the StatsContainer interface"(){
		given:
			def container = new GroovyStatsContainer( new StatsContainerImpl(new HistogramMetricFactory()))
		
		when:
			container.time("test_timer"){
				Thread.sleep(1000);
			}
			
		then:
			def sum = container.getSummary()
			sum.getMetrics().size() == 1
			def metric = sum.getMetrics().get("test_timer.millis")
			metric.getCount() == 1
			metric.getSum() >= 1000
			metric.getSum() < 2000
	}
	
	def "The stopAndStart should be able to be called from the closure"(){
		given:
			def container = new GroovyStatsContainer(new StatsContainerImpl(new HistogramMetricFactory()))
		
		when:
			container.time("test_timer"){
				Thread.sleep(1000);
				lap("step1")
				Thread.sleep(1000);
			}
			
		then:
			def sum = container.getSummary()
			sum.getMetrics().size() == 2
			def metric = sum.getMetrics().get("test_timer.millis")
			metric.getCount() == 1
			metric.getSum() >= 2000
			metric.getSum() < 3000
			
			def metric2 = sum.getMetrics().get("test_timer-step1.millis")
			metric2.getCount() == 1
			metric2.getSum() > 1000
			metric2.getSum() < 2000
	}
	
	def "Groovy register gauge will accept a closure that returns a double"(){
		given:
			def container = new GroovyStatsContainer(new StatsContainerImpl(new HistogramMetricFactory()))
		
		when:
			container.registerGauge("itsa_gauge"){
				// doing stuff
				System.currentTimeMillis()	
			}
		
		then:
			def g1 = container.gauges().get("itsa_gauge")
			def g2 = container.gauges().get("itsa_gauge")
			g1 < g2
	}
	
	def "Groovy register gauge will return 0 for a closure that doesn't return a number"(){
		given:
		def container = new GroovyStatsContainer(new StatsContainerImpl(new HistogramMetricFactory()))
	
		when:
			container.registerGauge("itsa_gauge"){
				// doing stuff
				"poop"
			}
		
		then:
			def g1 = container.gauges().get("itsa_gauge")
			g1 == 0
	}
	
	def "dummytest"(){
		expect:
			1 == 1
	}
}
