package com.presupuesto.transaccion.dto.response;

import com.presupuesto.transaccion.entity.SubTransaccion;

public record SubTransaccionResponse(Long id, Long categoriaId, long monto, String memo) {

    public static SubTransaccionResponse desde(SubTransaccion sub) {
        return new SubTransaccionResponse(
                sub.getId(),
                sub.getCategoria() == null ? null : sub.getCategoria().getId(),
                sub.getMonto(),
                sub.getMemo());
    }
}
