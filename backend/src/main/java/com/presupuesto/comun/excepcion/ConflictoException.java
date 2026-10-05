package com.presupuesto.comun.excepcion;

public class ConflictoException extends NegocioException {

    public ConflictoException(String mensaje) {
        super(CodigoError.CONFLICTO, mensaje);
    }

    public ConflictoException(CodigoError codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
