package com.example.smartcanteen.agent.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Builds deterministic Agent audit identifiers within the audit schema's 64-character limit. */
final class AgentAuditId {

    private static final String PREFIX = "AUDIT-AGENT-";
    private static final int DIGEST_LENGTH = 52;

    private AgentAuditId() {
    }

    static String forRun(String runId, String action) {
        return PREFIX + digest(runId + "\n" + action).substring(0, DIGEST_LENGTH);
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
