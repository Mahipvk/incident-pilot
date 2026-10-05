package com.incidentpilot.guard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Guardrail: masks secrets and personal data BEFORE any text reaches the LLM.
 * In regulated environments (finance, healthcare) this is a must-have, not a nice-to-have.
 */
public class Redactor {

    private static final Map<Pattern, String> RULES = new LinkedHashMap<>();

    static {
        RULES.put(Pattern.compile("(?i)(password|passwd|pwd|secret|token|api[_-]?key)\\s*[=:]\\s*\\S+"), "$1=[REDACTED]");
        RULES.put(Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._\\-]+"), "Bearer [REDACTED]");
        RULES.put(Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"), "[AWS_KEY]");
        RULES.put(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[EMAIL]");
        RULES.put(Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"), "[SSN]");
        RULES.put(Pattern.compile("\\b(?:\\d[ -]?){13,16}\\b"), "[CARD_OR_ACCOUNT]");
    }

    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        for (Map.Entry<Pattern, String> rule : RULES.entrySet()) {
            out = rule.getKey().matcher(out).replaceAll(rule.getValue());
        }
        return out;
    }
}
