package com.presupuesto.comun.excepcion;

import lombok.Getter;

@Getter
public abstract class NegocioException extends RuntimeException {

    private final CodigoError codigo;

    protected NegocioException(CodigoError codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }
}
