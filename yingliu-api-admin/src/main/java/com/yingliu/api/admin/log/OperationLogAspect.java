package com.yingliu.api.admin.log;

import com.yingliu.api.admin.auth.AdminAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.domain.system.OperationLog;
import com.yingliu.service.system.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartFile;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private final OperationLogMapper logMapper;
    private final AdminAuthService authService;
    private final ObjectMapper objectMapper;
    private final BlockingQueue<OperationLog> queue = new LinkedBlockingQueue<>(4096);

    private static final int MAX_PARAMS_LENGTH = 32768;

    public OperationLogAspect(OperationLogMapper logMapper, AdminAuthService authService, ObjectMapper objectMapper) {
        this.logMapper = logMapper;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    /**
     * Intercept all write operations in admin controllers.
     * Covers @PostMapping, @PatchMapping, @DeleteMapping methods.
     */
    @Around("within(com.yingliu.api.admin..*) && (" +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PatchMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public Object aroundWriteOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        OperationLog record = new OperationLog();
        record.setCreatedAt(LocalDateTime.now());

        // Populate common fields before execution
        populateActor(record);
        populateRequest(record);
        populateAnnotation(record, joinPoint);
        populateRequestParams(record, joinPoint);

        Object result = null;
        try {
            result = joinPoint.proceed();
            record.setStatus("SUCCESS");
        } catch (BusinessException ex) {
            record.setStatus("FAILURE");
            record.setErrorCode(ex.getCode());
            throw ex;
        } catch (MethodArgumentTypeMismatchException ex) {
            record.setStatus("FAILURE");
            record.setErrorCode("VALIDATION_FAILED");
            throw ex;
        } catch (Exception ex) {
            record.setStatus("FAILURE");
            record.setErrorCode("INTERNAL_ERROR");
            throw ex;
        } finally {
            record.setDurationMs((int) (System.currentTimeMillis() - start));
            inferDefaults(record, joinPoint);
            logInterface(record, joinPoint, result);
            if (!queue.offer(record)) {
                log.warn("Operation log queue full, dropping log: module={}, action={}", record.getModule(), record.getAction());
            }
        }
        return result;
    }

    private void logInterface(OperationLog record, ProceedingJoinPoint joinPoint, Object result) {
        if (!log.isInfoEnabled()) return;
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String method = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        log.info("Admin interface: method={}, path={}, action={}, status={}, durationMs={}, traceId={}, params={}, resultType={}",
                record.getRequestMethod(), record.getRequestPath(), method, record.getStatus(), record.getDurationMs(),
                record.getTraceId(), record.getRequestParams(), result == null ? "null" : result.getClass().getSimpleName());
    }

    private void populateActor(OperationLog record) {
        try {
            AdminAuthService.AdminSession session = authService.currentSession();
            record.setActorId(session.id());
            record.setActorName(session.username());
        } catch (Exception ex) {
            record.setActorId(0L);
            record.setActorName("anonymous");
        }
    }

    private void populateRequest(OperationLog record) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return;
        HttpServletRequest request = attrs.getRequest();
        record.setTraceId((String) request.getAttribute(TraceIdFilter.ATTRIBUTE));
        record.setIp(clientIp(request));
        record.setRequestMethod(request.getMethod());
        record.setRequestPath(request.getRequestURI());
    }

    private void populateRequestParams(OperationLog record, ProceedingJoinPoint joinPoint) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();

            // 1. Path variables
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object[] args = joinPoint.getArgs();
            Parameter[] parameters = method.getParameters();
            Map<String, Object> pathVars = new LinkedHashMap<>();
            for (int i = 0; i < parameters.length; i++) {
                PathVariable pv = parameters[i].getAnnotation(PathVariable.class);
                if (pv != null) {
                    String name = pv.value().isEmpty() ? parameters[i].getName() : pv.value();
                    pathVars.put(name, sanitize(args[i]));
                }
            }
            if (!pathVars.isEmpty()) params.put("pathVariables", pathVars);

            // 2. Query parameters / RequestParam
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                Map<String, Object> queryParams = new LinkedHashMap<>();
                // Extract @RequestParam annotated args
                for (int i = 0; i < parameters.length; i++) {
                    RequestParam rp = parameters[i].getAnnotation(RequestParam.class);
                    if (rp != null && args[i] != null) {
                        String name = rp.value().isEmpty() ? parameters[i].getName() : rp.value();
                        if (args[i] instanceof MultipartFile mf) {
                            queryParams.put(name, Map.of("fileName", mf.getOriginalFilename(), "size", mf.getSize()));
                        } else {
                            queryParams.put(name, sanitize(args[i]));
                        }
                    }
                }
                // Also include any query string params not already captured
                Map<String, String[]> paramMap = request.getParameterMap();
                for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
                    if (!queryParams.containsKey(entry.getKey())) {
                        String[] values = entry.getValue();
                        queryParams.put(entry.getKey(), values.length == 1 ? values[0] : List.of(values));
                    }
                }
                if (!queryParams.isEmpty()) params.put("queryParams", queryParams);
            }

            // 3. Request body
            for (int i = 0; i < parameters.length; i++) {
                RequestBody rb = parameters[i].getAnnotation(RequestBody.class);
                if (rb != null && args[i] != null) {
                    params.put("requestBody", sanitize(args[i]));
                    break;
                }
            }

            if (!params.isEmpty()) {
                String json = objectMapper.writeValueAsString(params);
                if (json.length() > MAX_PARAMS_LENGTH) {
                    json = json.substring(0, MAX_PARAMS_LENGTH) + "...(truncated)";
                }
                record.setRequestParams(json);
            }
        } catch (Exception ex) {
            log.debug("Failed to capture request params: {}", ex.getMessage());
        }
    }

    private Object sanitize(Object value) {
        if (value instanceof MultipartFile mf) {
            return Map.of("fileName", mf.getOriginalFilename() != null ? mf.getOriginalFilename() : "", "size", mf.getSize());
        }
        return value;
    }

    private void populateAnnotation(OperationLog record, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperLog annotation = method.getAnnotation(OperLog.class);
        if (annotation == null) {
            // Also check the class level — not typical but defensive
            annotation = joinPoint.getTarget().getClass().getAnnotation(OperLog.class);
        }
        if (annotation != null) {
            if (!annotation.module().isEmpty()) record.setModule(annotation.module());
            if (!annotation.action().isEmpty()) record.setAction(annotation.action());
            if (!annotation.targetType().isEmpty()) record.setTargetType(annotation.targetType());
            if (!annotation.detail().isEmpty()) record.setDetail(annotation.detail());
        }
    }

    private void inferDefaults(OperationLog record, ProceedingJoinPoint joinPoint) {
        // Infer module from controller class name if not set
        if (record.getModule() == null || record.getModule().isEmpty()) {
            String className = joinPoint.getTarget().getClass().getSimpleName();
            record.setModule(inferModule(className));
        }
        // Infer action from HTTP method + method name
        if (record.getAction() == null || record.getAction().isEmpty()) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            record.setAction(inferAction(signature.getMethod()));
        }
        // Infer target type from module if not set
        if (record.getTargetType() == null || record.getTargetType().isEmpty()) {
            record.setTargetType(record.getModule());
        }
        // Target id defaults to empty (single-path-variable extraction would require complex arg resolution)
        if (record.getTargetId() == null) {
            record.setTargetId("");
        }
    }

    private String inferModule(String controllerClassName) {
        // AdminVideoController -> VIDEO, AdminCategoryController -> CATEGORY, AdminTagController -> TAG
        String name = controllerClassName.replace("Controller", "").replace("Admin", "");
        // Handle "Moderation" -> "MODERATION", "Review" -> "REVIEW"
        return name.isEmpty() ? "UNKNOWN" : toUpperSnake(name);
    }

    private String inferAction(Method method) {
        String name = method.getName();
        // Common patterns
        if (name.contains("create") || name.contains("upload")) return "CREATE";
        if (name.contains("update") || name.contains("edit")) return "UPDATE";
        if (name.contains("delete") || name.contains("remove")) return "DELETE";
        if (name.contains("publish")) return "PUBLISH";
        if (name.contains("offShelf") || name.contains("off_shelf")) return "OFF_SHELF";
        if (name.contains("restore")) return "RESTORE";
        if (name.contains("approve")) return "APPROVE";
        if (name.contains("reject")) return "REJECT";
        if (name.contains("claim")) return "CLAIM";
        if (name.contains("submit")) return "SUBMIT";
        if (name.contains("hide")) return "HIDE";
        if (name.contains("mute")) return "MUTE";
        if (name.contains("suspend")) return "SUSPEND";
        if (name.contains("ban")) return "BAN";
        if (name.contains("revoke")) return "REVOKE";
        if (name.contains("unbind")) return "UNBIND";
        if (name.contains("login")) return "LOGIN";
        if (name.contains("logout")) return "LOGOUT";
        if (name.contains("batch")) return "BATCH";
        if (name.contains("retry")) return "RETRY";
        if (name.contains("cancel")) return "CANCEL";
        if (name.contains("resolve")) return "RESOLVE";
        if (name.contains("requestRevision")) return "REQUEST_REVISION";
        return toUpperSnake(name);
    }

    private static String toUpperSnake(String camel) {
        return camel.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Flush queued logs to database every second.
     */
    @Scheduled(fixedDelay = 1000)
    public void flush() {
        if (queue.isEmpty()) return;
        List<OperationLog> batch = new java.util.ArrayList<>();
        queue.drainTo(batch, 100);
        if (batch.isEmpty()) return;
        try {
            for (OperationLog record : batch) {
                logMapper.insert(record);
            }
        } catch (Exception ex) {
            log.error("Failed to flush operation logs (count={}): {}", batch.size(), ex.getMessage());
        }
    }
}
