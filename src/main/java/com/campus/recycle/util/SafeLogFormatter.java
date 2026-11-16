package com.campus.recycle.util;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/** 格式化安全元数据和无消息的异常栈，不调用 Throwable.getMessage/toString。 */
final class SafeLogFormatter extends Formatter {
    private final DateTimeFormatter timestamp;
    private record Trace(String relation, Throwable failure) { }

    SafeLogFormatter(ZoneId zone) {
        timestamp = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS", Locale.ROOT).withZone(zone);
    }

    @Override
    public String format(LogRecord record) {
        Object[] metadata = record.getParameters();
        String level = switch (record.getLevel().getName()) {
            case "SEVERE" -> "ERROR";
            case "WARNING" -> "WARN";
            case "FINE" -> "DEBUG";
            default -> "INFO";
        };
        StringBuilder output = new StringBuilder(timestamp.format(record.getInstant())).append(' ').append(level)
                .append(" class=\"").append(escape(record.getSourceClassName())).append("\" method=\"")
                .append(escape(record.getSourceMethodName())).append("\" user=")
                .append(metadata[0] == null ? "SYSTEM" : metadata[0]).append(" action=\"")
                .append(escape((String) metadata[1])).append("\" result=\"")
                .append(escape(record.getMessage())).append("\"\n");
        if (record.getThrown() != null) { appendTrace(output, record.getThrown()); }
        return output.toString();
    }

    private static void appendTrace(StringBuilder output, Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var remaining = new ArrayDeque<Trace>();
        remaining.push(new Trace("exception", failure));
        while (!remaining.isEmpty()) {
            Trace trace = remaining.pop();
            Throwable current = trace.failure();
            output.append(trace.relation()).append('=').append(current.getClass().getName());
            if (!seen.add(current)) { output.append(" [CIRCULAR_REFERENCE]\n"); continue; }
            output.append('\n');
            for (StackTraceElement frame : current.getStackTrace()) {
                output.append("  at ").append(escape(frame.toString())).append('\n');
            }
            if (current.getCause() != null) { remaining.push(new Trace("cause", current.getCause())); }
            Throwable[] suppressed = current.getSuppressed();
            for (int index = suppressed.length - 1; index >= 0; index--) {
                remaining.push(new Trace("suppressed", suppressed[index]));
            }
        }
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (Character.isHighSurrogate(character) && index + 1 < value.length()
                            && Character.isLowSurrogate(value.charAt(index + 1))) {
                        escaped.append(character).append(value.charAt(++index));
                    } else if (Character.isISOControl(character) || Character.isSurrogate(character)
                            || character == '\u2028' || character == '\u2029') {
                        escaped.append(String.format(Locale.ROOT, "\\u%04X", (int) character));
                    } else { escaped.append(character); }
                }
            }
        }
        return escaped.toString();
    }
}
