package com.presupuesto.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciales del login. El email se normaliza igual que en el registro; la contraseña se compara
 * tal como llega, sin recortar.
 */
public record LoginRequest(@NotBlank String email, @NotBlank String contrasena) {

    public LoginRequest {
        email = Normalizacion.email(email);
    }
}
