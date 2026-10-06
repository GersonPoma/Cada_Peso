package com.presupuesto.comun.excepcion;

/** Petición inválida por sí misma, pero que solo el service puede comprobar (400). */
public class DatosInvalidosException extends NegocioException {

    public DatosInvalidosException(String mensaje) {
        super(CodigoError.DATOS_INVALIDOS, mensaje);
    }
}
