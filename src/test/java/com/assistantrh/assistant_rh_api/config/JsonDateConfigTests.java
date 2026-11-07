package com.assistantrh.assistant_rh_api.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonDateConfigTests {

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new JsonDateConfig().datesSqlSansHeure())
            .build();

    @Test
    void ecritUneDateSqlSansHeureNiDecalageHoraire() {
        String json = mapper.writeValueAsString(Map.of(
                "date_debut", Date.valueOf(LocalDate.of(2012, 8, 13))
        ));

        assertEquals("{\"date_debut\":\"2012-08-13\"}", json);
    }
}
