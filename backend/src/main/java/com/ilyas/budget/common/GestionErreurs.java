package com.ilyas.budget.common;

import com.ilyas.budget.auth.EmailDejaUtiliseException;
import com.ilyas.budget.auth.IdentifiantsInvalidesException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GestionErreurs {

    @ExceptionHandler(EmailDejaUtiliseException.class)
    public ProblemDetail emailDejaUtilise(EmailDejaUtiliseException e) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        probleme.setTitle("Email déjà utilisé");
        return probleme;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Certains champs sont invalides");
        probleme.setTitle("Requête invalide");

        Map<String, String> erreurs = new HashMap<>();
        for (FieldError erreur : e.getBindingResult().getFieldErrors()) {
            erreurs.put(erreur.getField(), erreur.getDefaultMessage());
        }
        probleme.setProperty("erreurs", erreurs);
        return probleme;
    }

    @ExceptionHandler(IdentifiantsInvalidesException.class)
    public ProblemDetail identifiantsInvalides(IdentifiantsInvalidesException e) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        probleme.setTitle("Email ou mot de passe incorrect !");
        return probleme;
    }
}
