package com.presupuesto.transaccion.controller;

import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import com.presupuesto.transaccion.dto.request.ActualizarTransferenciaRequest;
import com.presupuesto.transaccion.dto.request.CrearTransferenciaRequest;
import com.presupuesto.transaccion.dto.response.TransferenciaResponse;
import com.presupuesto.transaccion.service.TransferenciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presupuestos/{presupuestoId}/transferencias")
@RequiredArgsConstructor
public class TransferenciaController {

    private final TransferenciaService transferenciaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferenciaResponse crear(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @Valid @RequestBody CrearTransferenciaRequest request) {
        return transferenciaService.crear(presupuestoId, usuario.id(), request);
    }

    @GetMapping("/{transaccionId}")
    public TransferenciaResponse obtener(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long transaccionId) {
        return transferenciaService.obtener(presupuestoId, usuario.id(), transaccionId);
    }

    @PutMapping("/{transaccionId}")
    public TransferenciaResponse actualizar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long transaccionId,
            @Valid @RequestBody ActualizarTransferenciaRequest request) {
        return transferenciaService.actualizar(
                presupuestoId, usuario.id(), transaccionId, request);
    }

    @DeleteMapping("/{transaccionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long transaccionId) {
        transferenciaService.borrar(presupuestoId, usuario.id(), transaccionId);
    }
}
