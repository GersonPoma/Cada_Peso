package com.presupuesto.conciliacion.dto.request;

import java.time.LocalDate;

/**
 * Datos para cerrar una conciliación. Sin anotaciones de Bean Validation a propósito: el service
 * comprueba {@code saldoExtracto} y {@code fecha} después de validar presupuesto y cuenta, para
 * que un recurso inexistente responda 404 antes que un cuerpo incompleto 400.
 * {@code crearAjuste} ausente equivale a falso.
 */
public record CrearConciliacionRequest(
        Long saldoExtracto, LocalDate fecha, Boolean crearAjuste, Long categoriaId) {

    public boolean pideAjuste() {
        return Boolean.TRUE.equals(crearAjuste);
    }
}
