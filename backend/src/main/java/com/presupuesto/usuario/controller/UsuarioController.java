package com.presupuesto.usuario.controller;

import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import com.presupuesto.usuario.dto.response.UsuarioActualResponse;
import com.presupuesto.usuario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/yo")
    public UsuarioActualResponse obtenerActual(
            @AuthenticationPrincipal UsuarioAutenticado usuarioAutenticado) {
        return usuarioService.obtenerActual(usuarioAutenticado.id());
    }
}
