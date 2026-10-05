package com.presupuesto.usuario.dto.response;

import com.presupuesto.comun.seguridad.Rol;
import com.presupuesto.usuario.entity.Perfil;
import java.time.LocalDate;

/** Datos del usuario autenticado. Nunca incluye la contraseña ni el id interno. */
public record UsuarioActualResponse(
        String email,
        Rol rol,
        String nombre,
        String apellido,
        LocalDate fechaNacimiento,
        String telefono,
        String monedaPredeterminada) {

    /** El perfil debe tener su usuario cargado (ver {@code PerfilRepository#findByUsuarioId}). */
    public static UsuarioActualResponse desde(Perfil perfil) {
        return new UsuarioActualResponse(
                perfil.getUsuario().getEmail(),
                perfil.getUsuario().getRol(),
                perfil.getNombre(),
                perfil.getApellido(),
                perfil.getFechaNacimiento(),
                perfil.getTelefono(),
                perfil.getMonedaPredeterminada());
    }
}
