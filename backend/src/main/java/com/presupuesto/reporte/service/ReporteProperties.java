package com.presupuesto.reporte.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Límite de los reportes por rango: cantidad máxima de meses entre {@code desde} y {@code hasta}
 * (ambos inclusive). Un rango mayor es un 400.
 */
@ConfigurationProperties("reportes")
public record ReporteProperties(@DefaultValue("60") int maxMeses) {

    public ReporteProperties {
        if (maxMeses < 1) {
            throw new IllegalArgumentException("reportes.max-meses debe ser al menos 1");
        }
    }
}
