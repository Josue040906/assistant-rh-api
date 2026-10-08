package com.assistantrh.assistant_rh_api;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenererHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String motDePasse = "Test1234!";
        String hash = encoder.encode(motDePasse);

        System.out.println("Mot de passe : " + motDePasse);
        System.out.println("Hash BCrypt  : " + hash);
    }
}
