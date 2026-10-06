package com.presupuesto.categoria.entity;

import com.presupuesto.comun.EntidadBase;
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
 * Categoría de un grupo. No se borra, se oculta; su {@code orden} (desde 0) es la posición dentro
 * del grupo y lo mantiene consecutivo el service.
 */
@Entity
@Table(
        name = "categorias",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_categorias_grupo_nombre",
                columnNames = {"grupo_id", "nombre_normalizado"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Categoria extends EntidadBase {

    /** Sin setter: solo cambia con {@link #moverAGrupo(GrupoCategoria)}. */
    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_id", nullable = false)
    private GrupoCategoria grupo;

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
    private boolean oculta = false;

    /** Sin setter: solo cambia con {@link #cambiarNota(String)}. {@code null} si no hay nota. */
    @Setter(AccessLevel.NONE)
    @Column(length = 500)
    private String nota;

    /** Forma comparable de un nombre ya recortado ({@code Locale.ROOT}, sin depender de la JVM). */
    public static String normalizar(String nombre) {
        return nombre.toLowerCase(Locale.ROOT);
    }

    /** Cambia el nombre (ya recortado) y mantiene sincronizado {@link #nombreNormalizado}. */
    public void renombrar(String nuevoNombre) {
        this.nombre = nuevoNombre;
        this.nombreNormalizado = normalizar(nuevoNombre);
    }

    /** La nota ya llega recortada y en {@code null} si quedó vacía. */
    public void cambiarNota(String nuevaNota) {
        this.nota = nuevaNota;
    }

    public void asignarOrden(int nuevoOrden) {
        this.orden = nuevoOrden;
    }

    public void moverAGrupo(GrupoCategoria nuevoGrupo) {
        this.grupo = nuevoGrupo;
    }

    public void ocultar() {
        this.oculta = true;
    }

    public void mostrar() {
        this.oculta = false;
    }
}
