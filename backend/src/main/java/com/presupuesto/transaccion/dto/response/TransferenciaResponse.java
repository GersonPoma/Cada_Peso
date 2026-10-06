package com.presupuesto.transaccion.dto.response;

import com.presupuesto.transaccion.entity.Transaccion;

/** Las dos patas de una transferencia: la salida (monto negativo) y la entrada (positivo). */
public record TransferenciaResponse(TransaccionResponse salida, TransaccionResponse entrada) {

    public static TransferenciaResponse desde(Transaccion salida, Transaccion entrada) {
        return new TransferenciaResponse(
                TransaccionResponse.desde(salida), TransaccionResponse.desde(entrada));
    }
}
