package com.tsu.notification.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsu.notification.dto.CreateNotificationRequest;
import com.tsu.notification.dto.NotificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * AWS Lambda handler for API Gateway events
 *
 * Handles HTTP requests from API Gateway and routes them to NotificationService
 *
 * Usage:
 * - Handler: com.tsu.notification.lambda.ApiGatewayLambdaHandler::handleRequest
 * - Runtime: java21
 * - Trigger: API Gateway (REST or HTTP API)
 */
@Slf4j
public class ApiGatewayLambdaHandler
    implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static ApplicationContext applicationContext;
    private static NotificationService notificationService;
    private static ObjectMapper objectMapper;

    static {
        // Initialize Spring context once (Lambda container reuse)
        try {
            log.info("Initializing Spring Application Context for Lambda");
            applicationContext = new AnnotationConfigApplicationContext(
                LambdaConfiguration.class
            );
            notificationService = applicationContext.getBean(NotificationService.class);
            objectMapper = applicationContext.getBean(ObjectMapper.class);
            log.info("Spring Application Context initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Spring Application Context", e);
            throw new RuntimeException("Lambda initialization failed", e);
        }
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
        APIGatewayProxyRequestEvent request,
        Context context
    ) {
        log.info("Lambda invoked: method={}, path={}, requestId={}",
            request.getHttpMethod(), request.getPath(), context.getRequestId());

        try {
            // Route based on HTTP method and path
            String method = request.getHttpMethod();
            String path = request.getPath();

            if ("POST".equals(method) && path.matches("/api/v1/notifications/?")) {
                return createNotification(request, context);
            } else if ("GET".equals(method) && path.matches("/api/v1/notifications/[^/]+")) {
                return getNotification(request, context);
            } else {
                return createResponse(404, Map.of("error", "Not Found"));
            }

        } catch (Exception e) {
            log.error("Error processing request", e);
            return createResponse(500, Map.of(
                "error", "Internal Server Error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Handle POST /api/v1/notifications
     */
    private APIGatewayProxyResponseEvent createNotification(
        APIGatewayProxyRequestEvent request,
        Context context
    ) {
        try {
            // Parse request body
            CreateNotificationRequest createRequest = objectMapper.readValue(
                request.getBody(),
                CreateNotificationRequest.class
            );

            // Create notification
            NotificationResponse response = notificationService.createNotification(createRequest);

            log.info("Notification created: id={}, requestId={}",
                response.getId(), context.getRequestId());

            return createResponse(201, response);

        } catch (Exception e) {
            log.error("Failed to create notification", e);
            return createResponse(400, Map.of(
                "error", "Bad Request",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Handle GET /api/v1/notifications/{id}
     */
    private APIGatewayProxyResponseEvent getNotification(
        APIGatewayProxyRequestEvent request,
        Context context
    ) {
        try {
            // Extract notification ID from path
            String[] pathParts = request.getPath().split("/");
            String notificationId = pathParts[pathParts.length - 1];

            // Get notification
            NotificationResponse response = notificationService.getNotification(
                java.util.UUID.fromString(notificationId)
            );

            return createResponse(200, response);

        } catch (IllegalArgumentException e) {
            return createResponse(400, Map.of(
                "error", "Bad Request",
                "message", "Invalid notification ID"
            ));
        } catch (Exception e) {
            log.error("Failed to get notification", e);
            return createResponse(404, Map.of(
                "error", "Not Found",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Create API Gateway response
     */
    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            headers.put("Access-Control-Allow-Headers", "Content-Type");

            return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(objectMapper.writeValueAsString(body));

        } catch (Exception e) {
            log.error("Failed to create response", e);
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(500)
                .withBody("{\"error\":\"Internal Server Error\"}");
        }
    }
}
