package com.assistantrh.assistant_rh_api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentDocumentInfoTests {

    @Test
    void accepteUnAgentSansPrenom() {
        AgentDocumentInfo agent = new AgentDocumentInfo(
                "Rabe", null, "AG-1", null, null, null,
                null, null, null, null, null
        );

        assertEquals("Rabe", agent.nomComplet());
    }

    @Test
    void traiteUnPrenomComposeDeBlancsCommeAbsent() {
        AgentDocumentInfo agent = new AgentDocumentInfo(
                "Rabe", "   ", "AG-1", null, null, null,
                null, null, null, null, null
        );

        assertEquals("Rabe", agent.nomComplet());
    }
}
