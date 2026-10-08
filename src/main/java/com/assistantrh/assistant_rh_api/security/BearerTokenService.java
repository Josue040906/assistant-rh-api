package com.assistantrh.assistant_rh_api.security;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@Component
public class BearerTokenService {

    private static final String JWT_HEADER =
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final long DURATION_SECONDS = 60 * 60;

    private final ObjectMapper objectMapper;
    private final String secret;

    public BearerTokenService(
            ObjectMapper objectMapper,
            @Value("${security.jwt.secret:}") String secret
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.secret = secret;
    }

    public String creerJeton(Integer utilisateurId) {
        if (utilisateurId == null || utilisateurId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'utilisateur connecté est invalide."
            );
        }

        long maintenant = Instant.now().getEpochSecond();
        Map<String, Object> claims = Map.of(
                "sub", utilisateurId.toString(),
                "iat", maintenant,
                "exp", maintenant + DURATION_SECONDS
        );

        try {
            String entete = encoder(JWT_HEADER.getBytes(StandardCharsets.UTF_8));
            String charge = encoder(objectMapper.writeValueAsBytes(claims));
            String contenu = entete + "." + charge;
            return contenu + "." + encoder(signature(contenu));
        } catch (JacksonException | GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Impossible de créer le jeton d'accès.",
                    exception
            );
        }
    }

    public Integer validerEtLireUtilisateur(String jeton) {
        if (jeton == null || jeton.isBlank()) {
            return null;
        }

        String[] morceaux = jeton.split("\\.", -1);
        if (morceaux.length != 3) {
            return null;
        }

        try {
            byte[] entete = decoder(morceaux[0]);
            Map<String, Object> enteteLue = objectMapper.readValue(
                    entete,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            if (!"HS256".equals(enteteLue.get("alg"))
                    || !"JWT".equals(enteteLue.get("typ"))) {
                return null;
            }

            String contenu = morceaux[0] + "." + morceaux[1];
            if (!MessageDigest.isEqual(
                    signature(contenu),
                    decoder(morceaux[2])
            )) {
                return null;
            }

            Map<String, Object> claims = objectMapper.readValue(
                    decoder(morceaux[1]),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            if (!(claims.get("sub") instanceof String sujet)
                    || !(claims.get("exp") instanceof Number expiration)
                    || expiration.longValue() <= Instant.now().getEpochSecond()) {
                return null;
            }

            int utilisateurId = Integer.parseInt(sujet);
            return utilisateurId > 0 ? utilisateurId : null;
        } catch (RuntimeException | GeneralSecurityException exception) {
            return null;
        }
    }

    private byte[] signature(String contenu) throws GeneralSecurityException {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "La variable JWT_SECRET doit contenir au moins 32 octets."
            );
        }

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
        return mac.doFinal(contenu.getBytes(StandardCharsets.US_ASCII));
    }

    private String encoder(byte[] valeur) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(valeur);
    }

    private byte[] decoder(String valeur) {
        return Base64.getUrlDecoder().decode(valeur);
    }
}
