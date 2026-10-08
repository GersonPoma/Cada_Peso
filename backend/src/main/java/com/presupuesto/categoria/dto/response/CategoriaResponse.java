package com.presupuesto.categoria.dto.response;

import com.presupuesto.categoria.entity.Categoria;
import java.time.Instant;

public record CategoriaResponse(
        Long id,
        Long grupoId,
        String nombre,
        int orden,
        boolean oculta,
        String nota,
        boolean esPagoTarjeta,
        Long cuentaId,
        Instant fechaCreacion,
        Instant fechaActualizacion) {

    public static CategoriaResponse desde(Categoria categoria) {
        return new CategoriaResponse(
                categoria.getId(),
                categoria.getGrupo().getId(),
                categoria.getNombre(),
                categoria.getOrden(),
                categoria.isOculta(),
                categoria.getNota(),
                categoria.esPagoTarjeta(),
                categoria.esPagoTarjeta() ? categoria.getCuentaTarjeta().getId() : null,
                categoria.getFechaCreacion(),
                categoria.getFechaActualizacion());
    }
}
