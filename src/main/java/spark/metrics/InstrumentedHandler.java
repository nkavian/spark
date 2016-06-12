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
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.AsyncContextState;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpChannelState;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.RatioGauge;
import com.codahale.metrics.Timer;

/**
 * A Jetty {@link Handler} which records various metrics about an underlying {@link Handler} instance.
 */
public class InstrumentedHandler extends HandlerWrapper {

    // the number of active dispatches
    private Counter              activeDispatches;

    // the number of active requests
    private Counter              activeRequests;
    // the number of requests currently suspended.
    private Counter              activeSuspended;

    // the number of requests that have been asynchronously dispatched
    private Meter                asyncDispatches;

    // the number of requests that expired while suspended
    private Meter                asyncTimeouts;

    private Timer                connectRequests;

    private Timer                deleteRequests;

    // the number of dispatches seen by this handler, excluding active
    private Timer                dispatches;

    private Timer                getRequests;

    private Timer                headRequests;

    private AsyncListener        listener;

    private final MetricRegistry metricRegistry;
    private Timer                moveRequests;
    private String               name;
    private Timer                optionsRequests;
    private Timer                otherRequests;
    private Timer                postRequests;
    private final String         prefix;
    private Timer                putRequests;
    // the requests handled by this handler, excluding active
    private Timer                requests;
    private Meter[]              responses;

    private Timer                traceRequests;

    /**
     * Create a new instrumented handler using a given metrics registry.
     *
     * @param registry the registry for the metrics
     *
     */
    public InstrumentedHandler(final MetricRegistry registry) {
        this(registry, null);
    }

    /**
     * Create a new instrumented handler using a given metrics registry.
     *
     * @param registry the registry for the metrics
     * @param serviceName the prefix to use for the metrics names
     *
     */
    public InstrumentedHandler(final MetricRegistry registry, final String serviceName) {
        metricRegistry = registry;
        prefix = "jetty.handler." + serviceName;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final String prefix = this.prefix == null ? name(getHandler().getClass(), name) : name(this.prefix, name);

        requests = metricRegistry.timer(name(prefix, "requests"));
        dispatches = metricRegistry.timer(name(prefix, "dispatches"));

        activeRequests = metricRegistry.counter(name(prefix, "active-requests"));
        activeDispatches = metricRegistry.counter(name(prefix, "active-dispatches"));
        activeSuspended = metricRegistry.counter(name(prefix, "active-suspended"));

        asyncDispatches = metricRegistry.meter(name(prefix, "async-dispatches"));
        asyncTimeouts = metricRegistry.meter(name(prefix, "async-timeouts"));

        responses = new Meter[] {
            metricRegistry.meter(name(prefix, "1xx-responses")), // 1xx
            metricRegistry.meter(name(prefix, "2xx-responses")), // 2xx
            metricRegistry.meter(name(prefix, "3xx-responses")), // 3xx
            metricRegistry.meter(name(prefix, "4xx-responses")), // 4xx
            metricRegistry.meter(name(prefix, "5xx-responses")) // 5xx
        };

        getRequests = metricRegistry.timer(name(prefix, "get-requests"));
        postRequests = metricRegistry.timer(name(prefix, "post-requests"));
        headRequests = metricRegistry.timer(name(prefix, "head-requests"));
        putRequests = metricRegistry.timer(name(prefix, "put-requests"));
        deleteRequests = metricRegistry.timer(name(prefix, "delete-requests"));
        optionsRequests = metricRegistry.timer(name(prefix, "options-requests"));
        traceRequests = metricRegistry.timer(name(prefix, "trace-requests"));
        connectRequests = metricRegistry.timer(name(prefix, "connect-requests"));
        moveRequests = metricRegistry.timer(name(prefix, "move-requests"));
        otherRequests = metricRegistry.timer(name(prefix, "other-requests"));

        metricRegistry.register(name(prefix, "percent-4xx-1m"), new RatioGauge() {

            @Override
            protected Ratio getRatio() {
                return Ratio.of(
                    responses[3].getOneMinuteRate(),
                    requests.getOneMinuteRate());
            }
        });

        metricRegistry.register(name(prefix, "percent-4xx-5m"), new RatioGauge() {

            @Override
            protected Ratio getRatio() {
                return Ratio.of(
                    responses[3].getFiveMinuteRate(),
                    requests.getFiveMinuteRate());
            }
        });

        metricRegistry.register(name(prefix, "percent-4xx-15m"), new RatioGauge() {

            @Override
            protected Ratio getRatio() {
                return Ratio.of(
                    responses[3].getFifteenMinuteRate(),
                    requests.getFifteenMinuteRate());
            }
        });

        metricRegistry.register(name(prefix, "percent-5xx-1m"), new RatioGauge() {

            @Override
            protected Ratio getRatio() {
                return Ratio.of(
                    responses[4].getOneMinuteRate(),
                    requests.getOneMinuteRate());
            }
        });

        metricRegistry.register(name(prefix, "percent-5xx-5m"), new RatioGauge() {

            @Override
            protected Ratio getRatio() {
                return Ratio.of(
                    responses[4].getFiveMinuteRate(),
                    requests.getFiveMinuteRate());
            }
        });

        metricRegistry.register(name(prefix, "percent-5xx-15m"), new RatioGauge() {

            @Override
            protected Ratio getRatio() {
                return Ratio.of(
                    responses[4].getFifteenMinuteRate(),
                    requests.getFifteenMinuteRate());
            }
        });

        listener = new AsyncListener() {

            private long startTime;

            @Override
            public void onComplete(final AsyncEvent event) throws IOException {
                final AsyncContextState state = (AsyncContextState)event.getAsyncContext();
                final HttpServletRequest request = (HttpServletRequest)state.getRequest();
                final HttpServletResponse response = (HttpServletResponse)state.getResponse();
                updateResponses(request, response, startTime);
                if (state.getHttpChannelState().getState() != HttpChannelState.State.DISPATCHED) {
                    activeSuspended.dec();
                }
            }

            @Override
            public void onError(final AsyncEvent event) throws IOException {
            }

            @Override
            public void onStartAsync(final AsyncEvent event) throws IOException {
                startTime = System.currentTimeMillis();
                event.getAsyncContext().addListener(this);
            }

            @Override
            public void onTimeout(final AsyncEvent event) throws IOException {
                asyncTimeouts.mark();
            }
        };
    }

    public String getName() {
        return name;
    }

    @Override
    public void handle(final String path,
                       final Request request,
                       final HttpServletRequest httpRequest,
                       final HttpServletResponse httpResponse)
        throws IOException,
            ServletException {

        activeDispatches.inc();

        final long start;
        final HttpChannelState state = request.getHttpChannelState();
        if (state.isInitial()) {
            // new request
            activeRequests.inc();
            start = request.getTimeStamp();
        } else {
            // resumed request
            start = System.currentTimeMillis();
            activeSuspended.dec();
            if (state.getState() == HttpChannelState.State.DISPATCHED) {
                asyncDispatches.mark();
            }
        }

        try {
            super.handle(path, request, httpRequest, httpResponse);
        } finally {
            final long now = System.currentTimeMillis();
            final long dispatched = now - start;

            activeDispatches.dec();
            dispatches.update(dispatched, TimeUnit.MILLISECONDS);

            if (state.isSuspended()) {
                if (state.isInitial()) {
                    state.addListener(listener);
                }
                activeSuspended.inc();
            } else if (state.isInitial()) {
                updateResponses(httpRequest, httpResponse, start);
            }
            // else onCompletion will handle it.
        }
    }

    private Timer requestTimer(final String method) {
        final HttpMethod m = HttpMethod.fromString(method);
        if (m == null) {
            return otherRequests;
        } else {
            switch (m) {
                case GET:
                    return getRequests;
                case POST:
                    return postRequests;
                case PUT:
                    return putRequests;
                case HEAD:
                    return headRequests;
                case DELETE:
                    return deleteRequests;
                case OPTIONS:
                    return optionsRequests;
                case TRACE:
                    return traceRequests;
                case CONNECT:
                    return connectRequests;
                case MOVE:
                    return moveRequests;
                default:
                    return otherRequests;
            }
        }
    }

    public void setName(final String name) {
        this.name = name;
    }

    private void updateResponses(final HttpServletRequest request, final HttpServletResponse response, final long start) {
        final int responseStatus = response.getStatus() / 100;
        if (responseStatus >= 1 && responseStatus <= 5) {
            responses[responseStatus - 1].mark();
        }
        activeRequests.dec();
        final long elapsedTime = System.currentTimeMillis() - start;
        requests.update(elapsedTime, TimeUnit.MILLISECONDS);
        requestTimer(request.getMethod()).update(elapsedTime, TimeUnit.MILLISECONDS);
    }
}
