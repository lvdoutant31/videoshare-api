package com.yingliu.api.app.analytics;

import com.yingliu.api.app.security.AppActorProvider;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.app.AppAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 埋点上报（API §8）：POST /analytics/events/batch，可选鉴权。
 */
@RestController
@ConditionalOnBean(AppAnalyticsService.class)
@RequestMapping("/api/v1/analytics")
public class AppAnalyticsController {
    private final AppAnalyticsService service;
    private final AppActorProvider actor;

    public AppAnalyticsController(AppAnalyticsService service, AppActorProvider actor) {
        this.service = service;
        this.actor = actor;
    }

    @PostMapping("/events/batch")
    public ApiResponse<Void> batch(@RequestBody BatchBody body, HttpServletRequest request) {
        Long userId = actor.optionalUserId();
        List<AppAnalyticsService.EventInput> events = body.events().stream()
                .map(e -> new AppAnalyticsService.EventInput(e.eventId(), e.playSessionId(), e.eventName(), e.occurredAt(), e.anonymousSessionId(),
                        userId != null ? userId : e.userId(), e.videoId(), e.objectId(), e.pageSource(),
                        e.clientVersion(), e.networkType(), (String) request.getAttribute(TraceIdFilter.ATTRIBUTE),
                        e.properties()))
                .toList();
        service.receive(events);
        return ApiResponse.ok(null, traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    }

    public record BatchBody(List<EventBody> events) { }
    public record EventBody(String eventId, String playSessionId, String eventName, String occurredAt, String anonymousSessionId, Long userId,
                            Long videoId, Long objectId, String pageSource, String clientVersion,
                            String networkType, java.util.Map<String, Object> properties) { }
}
