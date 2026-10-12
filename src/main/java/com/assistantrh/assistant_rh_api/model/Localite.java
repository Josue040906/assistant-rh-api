package com.assistantrh.assistant_rh_api.model;

public record Localite(
        Long id,
        String niveau,
        String nom,
        Long parentId,
        String codeSource,
        String origine
) {
}
