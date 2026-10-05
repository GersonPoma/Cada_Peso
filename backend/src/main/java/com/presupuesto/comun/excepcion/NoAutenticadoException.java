package com.presupuesto.comun.excepcion;

/**
 * 401 decidido por un controller o service (no por la cadena de filtros de seguridad), por
 * ejemplo un login con credenciales incorrectas.
 */
public class NoAutenticadoException extends NegocioException {

    /**
     * Mensaje del 401 {@link CodigoError#NO_AUTENTICADO}, compartido con el
     * {@code AuthenticationEntryPoint} para que todas esas respuestas sean idénticas.
     */
    public static final String MENSAJE_NO_AUTENTICADO =
            "Se requiere autenticación para acceder a este recurso";

    public NoAutenticadoException(CodigoError codigo, String mensaje) {
        super(codigo, mensaje);
    }
}
