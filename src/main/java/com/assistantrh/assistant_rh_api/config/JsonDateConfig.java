package com.assistantrh.assistant_rh_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Une colonne SQL DATE (java.sql.Date, renvoyée par JdbcTemplate) est une date
 * sans heure : elle est écrite « AAAA-MM-JJ ». Sans cela, Jackson l'écrivait en
 * horodatage UTC (« 2012-08-12T21:00:00.000Z » pour le 13 août à Madagascar),
 * ce qui décalait la date d'un jour pour qui ne lisait que la partie date.
 */
@Configuration
public class JsonDateConfig {

    @Bean
    public JacksonModule datesSqlSansHeure() {
        SimpleModule module = new SimpleModule("dates-sql-sans-heure");
        module.addSerializer(java.sql.Date.class, new ValueSerializer<>() {
            @Override
            public void serialize(
                    java.sql.Date value,
                    JsonGenerator generator,
                    SerializationContext context
            ) {
                generator.writeString(value.toLocalDate().toString());
            }
        });
        return module;
    }
}
