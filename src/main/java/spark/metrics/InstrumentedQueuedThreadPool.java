/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package spark.metrics;

import static com.codahale.metrics.MetricRegistry.name;
import java.util.concurrent.BlockingQueue;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.RatioGauge;

public class InstrumentedQueuedThreadPool extends QueuedThreadPool {

    private final MetricRegistry metricRegistry;

    public InstrumentedQueuedThreadPool(@Name("registry") final MetricRegistry registry, @Name("poolName") final String poolName) {
        this(registry, poolName, 200);
    }

    public InstrumentedQueuedThreadPool(@Name("registry") final MetricRegistry registry,
                                        @Name("poolName") final String poolName,
                                        @Name("maxThreads") final int maxThreads) {
        this(registry, poolName, maxThreads, 8);
    }

    public InstrumentedQueuedThreadPool(@Name("registry") final MetricRegistry registry,
                                        @Name("poolName") final String poolName,
                                        @Name("maxThreads") final int maxThreads,
                                        @Name("minThreads") final int minThreads) {
        this(registry, poolName, maxThreads, minThreads, 60000);
    }

    public InstrumentedQueuedThreadPool(@Name("registry") final MetricRegistry registry,
                                        @Name("poolName") final String poolName,
                                        @Name("maxThreads") final int maxThreads,
                                        @Name("minThreads") final int minThreads,
                                        @Name("idleTimeout") final int idleTimeout) {
        this(registry, poolName, maxThreads, minThreads, idleTimeout, null);
    }

    public InstrumentedQueuedThreadPool(@Name("registry") final MetricRegistry registry,
                                        @Name("poolName") final String poolName,
                                        @Name("maxThreads") final int maxThreads,
                                        @Name("minThreads") final int minThreads,
                                        @Name("idleTimeout") final int idleTimeout,
                                        @Name("queue") final BlockingQueue<Runnable> queue) {
        super(maxThreads, minThreads, idleTimeout, queue);
        metricRegistry = registry;
        setName("jetty.threads." + poolName);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        metricRegistry.register(name(getName(), "utilization"), new RatioGauge() {

            @Override
            protected Ratio getRatio() {
                return Ratio.of(getThreads() - getIdleThreads(), getThreads());
            }
        });
        metricRegistry.register(name(getName(), "utilization-max"), new RatioGauge() {

            @Override
            protected Ratio getRatio() {
                return Ratio.of(getThreads() - getIdleThreads(), getMaxThreads());
            }
        });
        metricRegistry.register(name(getName(), "size"), new Gauge<Integer>() {

            @Override
            public Integer getValue() {
                return getThreads();
            }
        });
        metricRegistry.register(name(getName(), "jobs"), new Gauge<Integer>() {

            @Override
            public Integer getValue() {
                // This assumes the QueuedThreadPool is using a BlockingArrayQueue or
                // ArrayBlockingQueue for its queue, and is therefore a constant-time operation.
                return getQueue().size();
            }
        });
    }
}
