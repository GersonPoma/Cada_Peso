package com.presupuesto.usuario.dto;

import com.presupuesto.comun.seguridad.Rol;
import java.time.LocalDate;

/** Datos del usuario autenticado. Nunca incluye la contraseña ni el id interno. */
public record UsuarioActualResponse(
        String email,
        Rol rol,
        String nombre,
        String apellido,
        LocalDate fechaNacimiento,
        String telefono,
        String monedaPredeterminada) {}
