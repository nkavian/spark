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

import java.util.List;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

public class InstrumentedConnectionFactory extends ContainerLifeCycle implements ConnectionFactory {

    private final ConnectionFactory connectionFactory;
    private final Timer             timer;

    public InstrumentedConnectionFactory(final MetricRegistry registry, final String serviceName, final ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
        timer = registry.timer("jetty.connection-factory." + serviceName + ".connections");
        addBean(connectionFactory);
    }

    @Override
    public String getProtocol() {
        return connectionFactory.getProtocol();
    }

    @Override
    public List<String> getProtocols() {
        return connectionFactory.getProtocols();
    }

    @Override
    public Connection newConnection(final Connector connector, final EndPoint endPoint) {
        final Connection connection = connectionFactory.newConnection(connector, endPoint);
        connection.addListener(new Connection.Listener() {

            private Timer.Context context;

            @Override
            public void onClosed(final Connection connection) {
                context.stop();
            }

            @Override
            public void onOpened(final Connection connection) {
                context = timer.time();
            }
        });
        return connection;
    }
}
