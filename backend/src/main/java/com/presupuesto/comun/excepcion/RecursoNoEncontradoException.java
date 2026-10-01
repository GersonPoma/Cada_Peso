package com.presupuesto.comun.excepcion;

public class RecursoNoEncontradoException extends NegocioException {

    public RecursoNoEncontradoException(String mensaje) {
        super(CodigoError.RECURSO_NO_ENCONTRADO, mensaje);
    }
}
