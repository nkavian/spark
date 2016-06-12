/*
 * Copyright 2016 - Per Wendel
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

import spark.metrics.InstrumentedHandler;
import org.eclipse.jetty.server.Handler;
import spark.embeddedserver.EmbeddedServer;
import spark.embeddedserver.EmbeddedServerFactory;
import spark.http.matching.MatcherFilter;
import spark.route.Routes;
import spark.staticfiles.StaticFilesConfiguration;

/**
 * Creates instances of embedded jetty containers.
 */
public class EmbeddedJettyFactory implements EmbeddedServerFactory {

    public EmbeddedServer create(String serviceName, Routes routeMatcher, StaticFilesConfiguration staticFilesConfiguration, boolean hasMultipleHandler) {
        MatcherFilter matcherFilter = new MatcherFilter(routeMatcher, staticFilesConfiguration, false, hasMultipleHandler);
        matcherFilter.init(null);

        Handler handler;
        if (JettyServer.ENABLE_JMX && serviceName != null && !serviceName.isEmpty()) {
            final InstrumentedHandler instrumented = new InstrumentedHandler(JettyServer.REGISTRY, serviceName);
            instrumented.setHandler(new JettyHandler(matcherFilter));
            handler = instrumented;
        } else {
            handler = new JettyHandler(matcherFilter);
        }
        return new EmbeddedJettyServer(handler);
    }

}
