package com.ootd.pickup.global.slack;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SlackErrorMessageFactory {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_STACK_TRACE_LINES = 20;
    private static final int MAX_TEXT_LENGTH = 2900;

    private SlackErrorMessageFactory() {
    }

    static Map<String, Object> buildPayload(RuntimeException exception, ErrorRequestContext context, String activeProfile) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(headerBlock());
        blocks.add(summaryBlock(exception, context, activeProfile));
        blocks.add(dividerBlock());
        blocks.add(messageBlock(exception));
        blocks.add(stackTraceBlock(exception));
        return Map.of("blocks", blocks);
    }

    private static Map<String, Object> headerBlock() {
        return Map.of(
                "type", "header",
                "text", Map.of("type", "plain_text", "text", "🚨 500 Internal Server Error 발생", "emoji", true)
        );
    }

    private static Map<String, Object> dividerBlock() {
        return Map.of("type", "divider");
    }

    private static Map<String, Object> summaryBlock(RuntimeException exception, ErrorRequestContext context, String activeProfile) {
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(field("발생 시각", context.occurredAt().format(TIMESTAMP_FORMAT)));
        fields.add(field("프로필", activeProfile));
        fields.add(field("Method", context.method()));
        fields.add(field("Path", buildPath(context)));
        fields.add(field("클라이언트 IP", context.clientIp()));
        fields.add(field("예외 타입", exception.getClass().getName()));
        return Map.of("type", "section", "fields", fields);
    }

    private static Map<String, Object> field(String label, String value) {
        String safeValue = (value == null || value.isBlank()) ? "-" : value;
        return Map.of("type", "mrkdwn", "text", "*" + label + ":*\n" + safeValue);
    }

    private static String buildPath(ErrorRequestContext context) {
        if (context.queryString() == null || context.queryString().isBlank()) {
            return context.uri();
        }
        return context.uri() + "?" + context.queryString();
    }

    private static Map<String, Object> messageBlock(RuntimeException exception) {
        String message = exception.getMessage() == null ? "(메시지 없음)" : exception.getMessage();
        return Map.of(
                "type", "section",
                "text", Map.of("type", "mrkdwn", "text", "*예외 메시지:*\n```" + truncate(message) + "```")
        );
    }

    private static Map<String, Object> stackTraceBlock(RuntimeException exception) {
        return Map.of(
                "type", "section",
                "text", Map.of("type", "mrkdwn", "text", "*스택 트레이스:*\n```" + truncate(formatStackTrace(exception)) + "```")
        );
    }

    private static String formatStackTrace(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        String[] lines = stringWriter.toString().split("\n");

        int limit = Math.min(lines.length, MAX_STACK_TRACE_LINES);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            builder.append(lines[i]).append("\n");
        }
        if (lines.length > limit) {
            builder.append("... (").append(lines.length - limit).append(" more)");
        }
        return builder.toString();
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH) + "\n... (truncated)";
    }
}
