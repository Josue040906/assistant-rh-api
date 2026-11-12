package com.assistantrh.assistant_rh_api.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Le grade qu'un agent prend quand sa colonne de la grille change (titularisation,
 * reclassement) : celui d'indice égal à son indice actuel, sinon le premier
 * indice supérieur — jamais un indice plus loin, pour ne pas sauter de grade.
 * Si aucun grade n'atteint l'indice actuel, le plus haut grade de la colonne.
 */
final class GradeParIndice {

    private GradeParIndice() {
    }

    /** Les options sont triées par indice croissant (champ « indice »). */
    static Optional<Map<String, Object>> retenu(List<Map<String, Object>> options, Integer indiceActuel) {
        if (options.isEmpty()) {
            return Optional.empty();
        }
        if (indiceActuel == null) {
            return Optional.of(options.get(0));
        }
        return Optional.of(options.stream()
                .filter(option -> option.get("indice") instanceof Number indice && indice.intValue() >= indiceActuel)
                .findFirst()
                .orElse(options.get(options.size() - 1)));
    }
}
