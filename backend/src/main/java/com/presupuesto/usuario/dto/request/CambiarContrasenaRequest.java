package com.presupuesto.usuario.dto.request;

import com.presupuesto.usuario.validacion.ContrasenaValida;
import jakarta.validation.constraints.NotBlank;

/**
 * Cambio de contraseña. Ninguna de las dos se modifica nunca (ni se recorta). Su {@code
 * toString()} no incluye las contraseñas, para que un log que imprima el request no las filtre.
 */
public record CambiarContrasenaRequest(
        @NotBlank String contrasenaActual, @ContrasenaValida String contrasenaNueva) {

    @Override
    public String toString() {
        return "CambiarContrasenaRequest[contrasenaActual=***, contrasenaNueva=***]";
    }
}
