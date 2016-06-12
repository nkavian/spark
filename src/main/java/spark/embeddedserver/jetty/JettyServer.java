/*
 * Copyright 2015 - Per Wendel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package spark.embeddedserver.jetty;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import spark.metrics.InstrumentedQueuedThreadPool;

/**
 * Creates Jetty Server instances.
 */
class JettyServer {

    static final boolean ENABLE_JMX;
    static final MetricRegistry REGISTRY;
    static final JmxReporter REPORTER;

    static {
        if (System.getProperty("com.sun.management.jmxremote") != null) {
            ENABLE_JMX = true;
            REGISTRY = new MetricRegistry();
            REPORTER = JmxReporter.forRegistry(REGISTRY).build();
            REPORTER.start();
        } else {
            ENABLE_JMX = false;
            REGISTRY = null;
            REPORTER = null;
        }
    }

    /**
     * Creates a Jetty server.
     *
     * @param maxThreads          maxThreads
     * @param minThreads          minThreads
     * @param threadTimeoutMillis threadTimeoutMillis
     * @return a new jetty server instance
     */
    public static Server create(String serviceName, int maxThreads, int minThreads, int threadTimeoutMillis) {
        Server server;

        if (maxThreads > 0) {
            int max = (maxThreads > 0) ? maxThreads : 200;
            int min = (minThreads > 0) ? minThreads : 8;
            int idleTimeout = (threadTimeoutMillis > 0) ? threadTimeoutMillis : 60000;

            QueuedThreadPool pool = ENABLE_JMX && serviceName != null && !serviceName.isEmpty()
                ? new InstrumentedQueuedThreadPool(JettyServer.REGISTRY, serviceName, max, min, idleTimeout)
                : new QueuedThreadPool(max, min, idleTimeout);
            server = new Server(pool);
        } else {
            QueuedThreadPool pool = ENABLE_JMX && serviceName != null && !serviceName.isEmpty()
                ? new InstrumentedQueuedThreadPool(JettyServer.REGISTRY, serviceName)
                : new QueuedThreadPool();
            server = new Server(pool);
        }

        return server;
    }

}
