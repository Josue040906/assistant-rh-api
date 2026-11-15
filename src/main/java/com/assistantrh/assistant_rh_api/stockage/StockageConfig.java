package com.assistantrh.assistant_rh_api.stockage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Le choix du stockage des fichiers : Supabase si SUPABASE_URL et
 * SUPABASE_SECRET_KEY sont fournis (en ligne), sinon le dossier uploads/
 * (poste de développement).
 */
@Configuration
public class StockageConfig {

    private static final Logger LOG = LoggerFactory.getLogger(StockageConfig.class);

    @Bean
    public StockageFichiers stockageFichiers(
            @Value("${SUPABASE_URL:}") String url,
            @Value("${SUPABASE_SECRET_KEY:}") String cleSecrete,
            @Value("${SUPABASE_BUCKET:sygpers}") String compartiment
    ) {
        StockageFichiers stockage = url.isBlank() || cleSecrete.isBlank()
                ? new StockageLocal(Path.of("uploads"))
                : new StockageSupabase(url, cleSecrete, compartiment);
        LOG.info("Stockage des fichiers : {}", stockage.description());
        return stockage;
    }
}
