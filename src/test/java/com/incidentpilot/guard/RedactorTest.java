package com.incidentpilot.guard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    private final Redactor redactor = new Redactor();

    @Test
    void masksSecretsAndPersonalData() {
        String in = "login failed user=jane.doe@bank.com password=Hunter2! token: abc.def "
                + "Authorization: Bearer eyJhbGciOi.xyz key AKIAABCDEFGHIJKLMNOP ssn 123-45-6789 card 4111 1111 1111 1111";
        String out = redactor.redact(in);

        assertThat(out).doesNotContain("jane.doe@bank.com", "Hunter2!", "eyJhbGciOi", "AKIAABCDEFGHIJKLMNOP",
                "123-45-6789", "4111 1111 1111 1111");
        assertThat(out).contains("[EMAIL]", "password=[REDACTED]", "[AWS_KEY]", "[SSN]", "[CARD_OR_ACCOUNT]");
    }

    @Test
    void leavesNormalLogLinesAlone() {
        String line = "2026-09-14T10:01:03Z ERROR [edge-gateway] 504 upstream timeout after 60000ms size=2147483648";
        assertThat(redactor.redact(line)).isEqualTo(line);
    }
}
