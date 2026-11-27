package com.lanveriq.server.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in DEBUG-only fixed reason codes; never include request data, keys or IDs. */
final class AgentRequestDiagnostics {
    private static final Logger LOG =
            LoggerFactory.getLogger("com.lanveriq.server.diagnostics.activation");

    private AgentRequestDiagnostics() {}

    static void event(String fixedCode) {
        LOG.debug("LANVERIQ_AGENT_DIAGNOSTIC {}", fixedCode);
    }
}
