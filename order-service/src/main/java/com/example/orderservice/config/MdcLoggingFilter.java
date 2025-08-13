package com.example.orderservice.config;

import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Filter that adds traceId and orderId to the MDC for structured logging.
 * This ensures all log messages include correlation IDs for better observability.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter implements Filter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String SPAN_ID_KEY = "spanId";
    private static final String ORDER_ID_KEY = "orderId";
    private static final String ORDER_ID_HEADER = "X-Order-Id";
    private static final String ORDER_ID_PARAM = "orderId";

    private final Tracer tracer;

    public MdcLoggingFilter(Optional<Tracer> tracer) {
        this.tracer = tracer.orElse(null);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        try {
            // Add trace information to MDC
            addTraceInfoToMdc();

            // Add orderId to MDC if present
            addOrderIdToMdc(request);

            chain.doFilter(request, response);
        } finally {
            // Clean up MDC to prevent memory leaks
            clearMdc();
        }
    }

    private void addTraceInfoToMdc() {
        if (tracer != null) {
            TraceContext traceContext = tracer.currentTraceContext().context();
            if (traceContext != null) {
                MDC.put(TRACE_ID_KEY, traceContext.traceId());
                MDC.put(SPAN_ID_KEY, traceContext.spanId());
            }
        }
    }

    private void addOrderIdToMdc(ServletRequest request) {
        String orderId = null;

        if (request instanceof HttpServletRequest httpRequest) {
            // Try to get orderId from header
            orderId = httpRequest.getHeader(ORDER_ID_HEADER);

            // If not in header, try path parameter
            if (orderId == null) {
                orderId = httpRequest.getParameter(ORDER_ID_PARAM);
            }

            // If not in parameter, try to extract from path
            if (orderId == null) {
                String path = httpRequest.getRequestURI();
                orderId = extractOrderIdFromPath(path);
            }
        }

        if (orderId != null && !orderId.isEmpty()) {
            MDC.put(ORDER_ID_KEY, orderId);
        }
    }

    private String extractOrderIdFromPath(String path) {
        // Extract orderId from paths like /orders/{orderId} or /api/orders/{orderId}
        String[] segments = path.split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            if ("orders".equals(segments[i]) && i + 1 < segments.length) {
                return segments[i + 1];
            }
        }
        return null;
    }

    private void clearMdc() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SPAN_ID_KEY);
        MDC.remove(ORDER_ID_KEY);
    }

    /**
     * Utility method to manually add orderId to MDC in service layers
     */
    public static void addOrderIdToMdc(String orderId) {
        if (orderId != null && !orderId.isEmpty()) {
            MDC.put(ORDER_ID_KEY, orderId);
        }
    }

    /**
     * Utility method to remove orderId from MDC
     */
    public static void removeOrderIdFromMdc() {
        MDC.remove(ORDER_ID_KEY);
    }
}
