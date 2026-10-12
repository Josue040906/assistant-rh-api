package com.assistantrh.assistant_rh_api.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class TelephoneService {

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
    private final String regionParDefaut;

    public TelephoneService(
            @Value("${telephone.region-par-defaut:MG}") String regionParDefaut
    ) {
        this.regionParDefaut = regionParDefaut.toUpperCase(Locale.ROOT);
    }

    public String normaliser(String telephone) {
        if (telephone == null || telephone.isBlank()) {
            return null;
        }

        try {
            var numero = phoneNumberUtil.parse(telephone.trim(), regionParDefaut);
            if (!phoneNumberUtil.isValidNumber(numero)) {
                throw new TelephoneInvalideException(
                        "Le numéro de téléphone n'est pas valide. "
                                + "Vérifiez l'indicatif et le numéro saisi."
                );
            }
            return phoneNumberUtil.format(
                    numero,
                    PhoneNumberUtil.PhoneNumberFormat.E164
            );
        } catch (NumberParseException exception) {
            throw new TelephoneInvalideException(
                    "Le numéro de téléphone n'est pas valide. "
                            + "Pour Madagascar, saisissez par exemple 034 12 345 67 "
                            + "ou +261 34 12 345 67.",
                    exception
            );
        }
    }
}
