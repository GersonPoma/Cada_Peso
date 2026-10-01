package com.presupuesto.comun.excepcion;

public class ReglaNegocioException extends NegocioException {

    public ReglaNegocioException(String mensaje) {
        super(CodigoError.REGLA_NEGOCIO_VIOLADA, mensaje);
    }
}
