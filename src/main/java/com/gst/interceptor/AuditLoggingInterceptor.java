package com.gst.interceptor;

import com.gst.entity.ApiAuditLog;
import com.gst.repository.ApiAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class AuditLoggingInterceptor implements HandlerInterceptor {

    @Autowired
    private ApiAuditLogRepository auditLogRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        request.setAttribute("startTime", System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        try {
            long startTime = (long) request.getAttribute("startTime");
            long responseTime = System.currentTimeMillis() - startTime;
            String clientId = request.getHeader("X-Client-ID");

            if (clientId != null) {
                ApiAuditLog auditLog = ApiAuditLog.builder()
                        .clientId(clientId)
                        .endpoint(request.getRequestURI())
                        .httpMethod(request.getMethod())
                        .httpStatusCode(response.getStatus())
                        .responseTimeMs(responseTime)
                        .errorMessage(ex != null ? ex.getMessage() : null)
                        .build();

                auditLogRepository.save(auditLog);
            }
        } catch (Exception e) {
            log.error("Error logging audit trail: {}", e.getMessage(), e);
        }
    }
}
