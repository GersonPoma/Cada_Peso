package com.presupuesto.comun.paginacion;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Página de resultados de una lista paginada; la API nunca devuelve un {@code Page} de Spring. */
public record PaginaResponse<T>(
        List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public static <E, T> PaginaResponse<T> desde(Page<E> pagina, Function<E, T> mapeo) {
        return new PaginaResponse<>(
                pagina.getContent().stream().map(mapeo).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}
