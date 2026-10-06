package com.presupuesto.presupuesto.entity;

import com.presupuesto.comun.EntidadBase;
import com.presupuesto.usuario.entity.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Presupuesto de una persona: de él cuelgan sus cuentas, categorías y transacciones. La moneda se
 * fija al crearlo y no se puede cambiar.
 */
@Entity
@Table(
        name = "presupuestos",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_presupuestos_usuario_nombre",
                columnNames = {"usuario_id", "nombre_normalizado"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Presupuesto extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Sin setter: solo cambia con {@link #renombrar(String)}, que mantiene el campo derivado. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 100)
    private String nombre;

    /** {@link #nombre} en minúsculas: sostiene la unicidad sin distinguir mayúsculas. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 100)
    private String nombreNormalizado;

    @Column(nullable = false, length = 3, updatable = false)
    private String moneda;

    /**
     * Forma comparable de un nombre ya recortado. {@code Locale.ROOT} evita que el idioma de la
     * JVM cambie el resultado (ej. la {@code I} turca); no aplica el plegado completo de Unicode.
     */
    public static String normalizar(String nombre) {
        return nombre.toLowerCase(Locale.ROOT);
    }

    /** Cambia el nombre (ya recortado) y mantiene sincronizado {@link #nombreNormalizado}. */
    public void renombrar(String nuevoNombre) {
        this.nombre = nuevoNombre;
        this.nombreNormalizado = normalizar(nuevoNombre);
    }
}
