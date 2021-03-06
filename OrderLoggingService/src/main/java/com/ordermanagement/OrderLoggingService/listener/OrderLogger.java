package com.ordermanagement.OrderLoggingService.listener;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.web.internal.RequestTelemetryContext;
import com.microsoft.applicationinsights.web.internal.ThreadContext;
import com.microsoft.applicationinsights.web.internal.correlation.tracecontext.Traceparent;
import com.ordermanagement.OrderLoggingService.config.RabitConfig;
import com.ordermanagement.OrderLoggingService.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class OrderLogger {

    private static final Logger logger = LoggerFactory.getLogger(OrderLogger.class);

    @Autowired
    TelemetryClient telemetryClient;

    @RabbitListener(queues = RabitConfig.QUEUE_ORDERS)
    public void processOrder(Order order, Message message) {

        // Retrieve the parent correlation-id from the incoming message.
        String parentCorrelationId = message.getMessageProperties().getCorrelationId();
        RequestTelemetry requestTelemetry = createRequestTelemetry(parentCorrelationId);
        logger.info("order received " + order.toString());
        logger.info("orderNumber " + order.getOrderNumber());
        logger.info("productId " + order.getProductId());
        logger.info("parent correlationId: " + parentCorrelationId);
        telemetryClient.trackRequest(requestTelemetry);
    }

    /**
     * A method to create RequestTelemetry from parentCorrelationId
     * @param parentCorrelationId
     * @return RequestTelemetry
     */
    private RequestTelemetry createRequestTelemetry(String parentCorrelationId) {
        RequestTelemetryContext requestTelemetryContext = new RequestTelemetryContext(new Date().getTime());
        RequestTelemetry requestTelemetry = requestTelemetryContext.getHttpRequestTelemetry();

        // Get Incoming TraceId from the parentCorrelationID (AI Format)
        String incomingTraceId = getIncomingTraceParent(parentCorrelationId);

        // Create a new traceparent for child request
        Traceparent traceparent = new Traceparent(0, incomingTraceId, null, 0);

        // Set the Id of the RequestTelemetry
        requestTelemetry.setId("|" + traceparent.getTraceId() + "." + traceparent.getSpanId()
                + ".");

        // set operation id as W3C TraceID
        requestTelemetry.getContext().getOperation().setId(incomingTraceId);

        // Set parent id of the request as the incoming parentId
        requestTelemetry.getContext().getOperation().setParentId(parentCorrelationId);

        requestTelemetry.setName("Process message");
        requestTelemetry.setSource("type:RabbitMQ | " + RabitConfig.QUEUE_ORDERS);

        // Set the RequestTelemetryContext in the TLS.
        ThreadContext.setRequestTelemetryContext(requestTelemetryContext);
        return requestTelemetry;
    }

    private String getIncomingTraceParent(String diagnosticId) {
        diagnosticId = diagnosticId.substring(1, diagnosticId.length()-1);
        return diagnosticId.split("\\.")[0];
    }

}
