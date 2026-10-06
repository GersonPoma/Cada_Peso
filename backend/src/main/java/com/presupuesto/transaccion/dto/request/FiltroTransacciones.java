package com.presupuesto.transaccion.dto.request;

import com.presupuesto.transaccion.entity.EstadoTransaccion;
import java.time.LocalDate;

/** Filtros opcionales de la lista; {@code q} se recorta y vacío pasa a {@code null}. */
public record FiltroTransacciones(
        Long cuentaId,
        Long categoriaId,
        LocalDate desde,
        LocalDate hasta,
        EstadoTransaccion estado,
        boolean soloSinAprobar,
        String q) {

    public FiltroTransacciones {
        q = Normalizacion.recortarONulo(q);
    }
}
