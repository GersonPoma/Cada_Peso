package com.presupuesto.importacion.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Límites de la importación: bytes del archivo y filas de datos. Superarlos es un 400. El techo
 * del transporte multipart ({@code spring.servlet.multipart.*}) debe ser mayor que
 * {@code maxBytes}.
 */
@ConfigurationProperties("importacion")
public record ImportacionProperties(
        @DefaultValue("2097152") long maxBytes, @DefaultValue("5000") int maxFilas) {}
