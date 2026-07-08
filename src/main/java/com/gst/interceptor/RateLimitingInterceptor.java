package com.gst.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gst.dto.ApiResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    private static final int REQUESTS_PER_MINUTE = 100;
    private static final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Skip rate limiting for auth and health endpoints
        String requestURI = request.getRequestURI();
        if (requestURI.contains("/auth/") || requestURI.contains("/health")) {
            return true;
        }

        String clientId = request.getHeader("X-Client-ID");
        if (clientId == null) {
            clientId = request.getRemoteAddr(); // Fallback to IP address
        }

        Bucket bucket = buckets.computeIfAbsent(clientId, k -> createNewBucket());

        if (bucket.tryConsume(1)) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(bucket.estimateAbilityToConsume(1)));
            return true;
        } else {
            log.warn("Rate limit exceeded for client: {}", clientId);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiResponse<?> errorResponse = ApiResponse.builder()
                    .success(false)
                    .statusCode("RATE_LIMIT_EXCEEDED")
                    .message("Too many requests. Rate limit: " + REQUESTS_PER_MINUTE + " requests per minute")
                    .httpStatusCode(429)
                    .build();

            response.getWriter().write(new ObjectMapper().writeValueAsString(errorResponse));
            return false;
        }
    }

    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.classic(REQUESTS_PER_MINUTE, Refill.intervally(REQUESTS_PER_MINUTE, Duration.ofMinutes(1)));
        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }
}
