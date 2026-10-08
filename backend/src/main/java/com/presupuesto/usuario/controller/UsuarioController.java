package com.presupuesto.usuario.controller;

import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import com.presupuesto.usuario.dto.request.ActualizarNombreRequest;
import com.presupuesto.usuario.dto.request.CambiarContrasenaRequest;
import com.presupuesto.usuario.dto.response.UsuarioActualResponse;
import com.presupuesto.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @PutMapping("/yo")
    public UsuarioActualResponse cambiarNombre(
            @AuthenticationPrincipal UsuarioAutenticado usuarioAutenticado,
            @Valid @RequestBody ActualizarNombreRequest request) {
        return usuarioService.cambiarNombre(usuarioAutenticado.id(), request);
    }

    @PostMapping("/yo/contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarContrasena(
            @AuthenticationPrincipal UsuarioAutenticado usuarioAutenticado,
            @Valid @RequestBody CambiarContrasenaRequest request) {
        usuarioService.cambiarContrasena(usuarioAutenticado.id(), request);
    }
}
