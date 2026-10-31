package com.ilyas.budget.common;

public class RessourceIntrouvableException extends RuntimeException {
    public RessourceIntrouvableException() {
        super("Ressource introuvable");
    }
}
