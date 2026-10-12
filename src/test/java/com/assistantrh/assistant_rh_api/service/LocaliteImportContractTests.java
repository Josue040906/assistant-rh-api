package com.assistantrh.assistant_rh_api.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocaliteImportContractTests {

    @Test
    void importEstTransactionnelEtRejouableSansDupliquerLesCodesSource()
            throws IOException {
        Path importer = Path.of(
                "scripts",
                "import_localites_madagascar.py"
        );
        String source = Files.readString(importer, StandardCharsets.UTF_8);

        assertTrue(source.contains("ON CONFLICT (source_name, source_code)"));
        assertTrue(source.contains("DO UPDATE SET"));
        assertTrue(source.contains("\"BEGIN;\""));
        assertTrue(source.contains("\"COMMIT;\""));
        assertTrue(source.contains("RAISE EXCEPTION"));
        assertTrue(source.contains("ON_ERROR_STOP"));
        assertTrue(List.of("REGION", "DISTRICT", "COMMUNE", "FOKONTANY")
                .stream().allMatch(source::contains));
    }
}
