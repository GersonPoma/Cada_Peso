package com.presupuesto.auth;

import com.presupuesto.comun.validacion.MaximoBytesUtf8;
import com.presupuesto.comun.validacion.MonedaValida;
import com.presupuesto.usuario.MayorDeEdad;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Datos del registro. El constructor compacto normaliza los campos antes de que se validen (Jackson
 * construye el record con él): las validaciones ven ya los valores recortados. La contraseña nunca
 * se modifica.
 */
public record RegistroRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) @MaximoBytesUtf8(72) String contrasena,
        @NotBlank @Size(min = 2, max = 100) String nombre,
        @NotBlank @Size(min = 2, max = 100) String apellido,
        @NotNull @MayorDeEdad LocalDate fechaNacimiento,
        @Size(max = 20) String telefono,
        @MonedaValida String monedaPredeterminada) {

    public static final String MONEDA_POR_DEFECTO = "BOB";

    public RegistroRequest {
        email = Normalizacion.email(email);
        nombre = Normalizacion.recortar(nombre);
        apellido = Normalizacion.recortar(apellido);
        telefono = Normalizacion.recortarONulo(telefono);
        monedaPredeterminada =
                monedaPredeterminada == null ? MONEDA_POR_DEFECTO : monedaPredeterminada;
    }
}
