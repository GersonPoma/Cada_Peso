package com.presupuesto.reporte.dto.response;

import com.presupuesto.categoria.entity.Categoria;
import java.util.function.LongUnaryOperator;

/** Gasto neto de una categoría en el rango (milésimas) y su porcentaje del total del reporte. */
public record CategoriaGastoResponse(
        Long categoriaId, String nombre, boolean oculta, long total, long porcentaje) {

    /**
     * @param porcentajeDe convierte un total en su porcentaje (centésimas de punto) del total del
     *     reporte
     */
    public static CategoriaGastoResponse desde(
            Categoria categoria, long total, LongUnaryOperator porcentajeDe) {
        return new CategoriaGastoResponse(
                categoria.getId(),
                categoria.getNombre(),
                categoria.isOculta(),
                total,
                porcentajeDe.applyAsLong(total));
    }
}
