package com.presupuesto.comun.seguridad;

/** Principal de una petición autenticada con JWT; se obtiene con @AuthenticationPrincipal. */
public record UsuarioAutenticado(Long id, Rol rol) {}
