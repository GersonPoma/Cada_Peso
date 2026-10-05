package com.presupuesto.comun.seguridad;

import com.presupuesto.usuario.Rol;

/** Principal de una petición autenticada con JWT; se obtiene con @AuthenticationPrincipal. */
public record UsuarioAutenticado(Long id, Rol rol) {}
