package com.presupuesto.meta.dto.response;

import java.time.YearMonth;
import java.util.List;

/** Estado de las metas de un mes; {@code totalFaltante} suma el faltante de {@code metas}. */
public record MetasMesResponse(String mes, long totalFaltante, List<MetaMesResponse> metas) {

    public static MetasMesResponse desde(YearMonth mes, List<MetaMesResponse> metas) {
        long totalFaltante = metas.stream().mapToLong(MetaMesResponse::faltante).sum();
        return new MetasMesResponse(mes.toString(), totalFaltante, metas);
    }
}
