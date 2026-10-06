package com.presupuesto.categoria.entity;

import com.presupuesto.comun.EntidadBase;
import com.presupuesto.presupuesto.entity.Presupuesto;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Grupo de categorías de un presupuesto. Los grupos no se borran, se ocultan; su {@code orden}
 * (desde 0) lo mantiene consecutivo el service.
 */
@Entity
@Table(
        name = "grupos_categoria",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_grupos_categoria_presupuesto_nombre",
                columnNames = {"presupuesto_id", "nombre_normalizado"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class GrupoCategoria extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presupuesto_id", nullable = false)
    private Presupuesto presupuesto;

    /** Sin setter: solo cambia con {@link #renombrar(String)}, que mantiene el campo derivado. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 100)
    private String nombre;

    /** {@link #nombre} en minúsculas: sostiene la unicidad sin distinguir mayúsculas. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 100)
    private String nombreNormalizado;

    /** Sin setter: solo cambia con {@link #asignarOrden(int)}. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private int orden;

    /** Sin setter: solo cambia con {@link #ocultar()} y {@link #mostrar()}. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    @Builder.Default
    private boolean oculto = false;

    /** Forma comparable de un nombre ya recortado ({@code Locale.ROOT}, sin depender de la JVM). */
    public static String normalizar(String nombre) {
        return nombre.toLowerCase(Locale.ROOT);
    }

    /** Cambia el nombre (ya recortado) y mantiene sincronizado {@link #nombreNormalizado}. */
    public void renombrar(String nuevoNombre) {
        this.nombre = nuevoNombre;
        this.nombreNormalizado = normalizar(nuevoNombre);
    }

    public void asignarOrden(int nuevoOrden) {
        this.orden = nuevoOrden;
    }

    public void ocultar() {
        this.oculto = true;
    }

    public void mostrar() {
        this.oculto = false;
    }
}
