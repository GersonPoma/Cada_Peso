package com.presupuesto.presupuesto.dto.request;

import com.presupuesto.comun.validacion.MonedaValida;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear un presupuesto. El constructor compacto recorta el nombre antes de validarlo.
 * La moneda es opcional: si falta, el service usa la moneda predeterminada del perfil.
 */
public record CrearPresupuestoRequest(
        @NotBlank @Size(max = 100) String nombre, @MonedaValida String moneda) {

    public CrearPresupuestoRequest {
        nombre = nombre == null ? null : nombre.strip();
    }
}
