package com.stephanofer.friendsSystem;

import java.util.Locale;

public enum Language {
    ES("es"),
    EN("en");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    public String code() {
        return this.code;
    }

    public static Language fromCode(String raw) {
        return "es".equalsIgnoreCase(raw) ? ES : EN;
    }

    public static Language fromLocale(Locale locale, Language fallback) {
        if (locale == null) {
            return fallback;
        }
        String language = locale.getLanguage();
        if ("es".equalsIgnoreCase(language)) {
            return ES;
        }
        if ("en".equalsIgnoreCase(language)) {
            return EN;
        }
        return fallback;
    }
}
