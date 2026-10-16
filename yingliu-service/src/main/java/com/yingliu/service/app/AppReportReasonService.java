package com.yingliu.service.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

@Service
@Profile("!test")
public class AppReportReasonService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AppReportReasonService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<ReportReason> list(TargetType targetType) {
        return jdbc.query("""
                SELECT reason_code, reason_name, target_types
                FROM sys_report_reason
                WHERE status = 'ENABLED' AND JSON_CONTAINS(target_types, JSON_QUOTE(?))
                ORDER BY sort, id
                """, (rs, rowNum) -> {
            try {
                List<TargetType> targetTypes = objectMapper.readValue(rs.getString("target_types"), new TypeReference<>() { });
                return new ReportReason(rs.getString("reason_code"), rs.getString("reason_name"), targetTypes);
            } catch (JsonProcessingException e) {
                throw new SQLException("Invalid report reason target types", e);
            }
        }, targetType.name());
    }

    public enum TargetType { VIDEO, COMMENT, USER }

    public record ReportReason(String code, String name, List<TargetType> targetTypes) { }
}
