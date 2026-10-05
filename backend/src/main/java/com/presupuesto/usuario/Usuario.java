package com.presupuesto.usuario;

import com.presupuesto.comun.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Credenciales de una persona: solo lo necesario para autenticarla. */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario extends EntidadBase {

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    /** Hash BCrypt (60 caracteres); 100 deja margen para un prefijo como {@code {bcrypt}}. */
    @Column(nullable = false, length = 100)
    private String contrasena;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Rol rol = Rol.USUARIO;
}
