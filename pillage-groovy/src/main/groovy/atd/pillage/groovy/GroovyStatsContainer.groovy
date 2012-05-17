package atd.pillage.groovy

import atd.pillage.StatsContainer
import atd.pillage.Gauge

class GroovyStatsContainer implements StatsContainer {
	
	@Delegate private StatsContainer container;

	GroovyStatsContainer(StatsContainer container){
		this.container = container;
	}
	
	def time(String timerName, Closure c ) {
		def timer = container.getTimer(timerName)
		c.delegate = timer
		c.resolveStrategy = Closure.DELEGATE_FIRST
		timer.start()
		def returnValue = c()
		timer.stop()
        returnValue
	} 
	
	def registerGauge(String gaugeName, Closure c ) {
		container.registerGauge(gaugeName, new Gauge() {
			double read(){
				def ret = 0
				try {
					ret = (double) c()
				} catch (ClassCastException) {
					// ignore return 0
				}
				ret
			}
		})
	}
}
