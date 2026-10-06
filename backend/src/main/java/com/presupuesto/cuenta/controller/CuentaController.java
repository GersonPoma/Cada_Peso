package com.presupuesto.cuenta.controller;

import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import com.presupuesto.cuenta.dto.request.ActualizarCuentaRequest;
import com.presupuesto.cuenta.dto.request.CrearCuentaRequest;
import com.presupuesto.cuenta.dto.response.CuentaResponse;
import com.presupuesto.cuenta.service.CuentaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presupuestos/{presupuestoId}/cuentas")
@RequiredArgsConstructor
public class CuentaController {

    private final CuentaService cuentaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CuentaResponse crear(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @Valid @RequestBody CrearCuentaRequest request) {
        return cuentaService.crear(presupuestoId, usuario.id(), request);
    }

    @GetMapping
    public List<CuentaResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @RequestParam(defaultValue = "false") boolean incluirCerradas) {
        return cuentaService.listar(presupuestoId, usuario.id(), incluirCerradas);
    }

    @GetMapping("/{id}")
    public CuentaResponse obtener(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return cuentaService.obtener(presupuestoId, usuario.id(), id);
    }

    @PutMapping("/{id}")
    public CuentaResponse actualizar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id,
            @Valid @RequestBody ActualizarCuentaRequest request) {
        return cuentaService.actualizar(presupuestoId, usuario.id(), id, request);
    }

    @PostMapping("/{id}/cerrar")
    public CuentaResponse cerrar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return cuentaService.cerrar(presupuestoId, usuario.id(), id);
    }

    @PostMapping("/{id}/reabrir")
    public CuentaResponse reabrir(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return cuentaService.reabrir(presupuestoId, usuario.id(), id);
    }
}
