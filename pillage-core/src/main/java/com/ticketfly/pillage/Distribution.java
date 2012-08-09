/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ticketfly.pillage;

import java.util.Map;

/**
 * Metrics are represented at a statistical distribution.
 */
public interface Distribution {
	
	/**
	 * get the number of metrics collected
	 * @return
	 */
    public long getCount();
    
    /**
     * get the sum of all the metric values
     * @return
     */
    public long getSum();
    
    /**
     * get the minimum value metric collected
     * @return
     */
    public long getMinimum();
    
    /**
     * get the maximum value metric collected
     * @return
     */
    public long getMaximum();
    
    /**
     * get the mean value for the metrics collected
     * @return
     */
    public double getMean();
    
    /**
     * return the distribution as a map of statistics comprising 
     * this metric.
     * 
     * @return
     */
    public Map<String,Number> toMap();

    /**
     * Create a new distribution by subtracting another distribution from this one. This is useful for creating deltas.
     *
     * An IllegalArgumentException is thrown if the Distributions are incompatible types.
     * @param dist
     * @return Distribution
     * @throws IllegalArgumentException
     */
    public Distribution delta( Distribution dist ) throws IllegalArgumentException;

}
