package com.presupuesto.comun.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

public class MonedaValidaValidator implements ConstraintValidator<MonedaValida, String> {

    private static final Set<String> CODIGOS_DE_MONEDA = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        return valor == null || CODIGOS_DE_MONEDA.contains(valor);
    }
}
