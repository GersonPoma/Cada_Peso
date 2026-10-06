package com.presupuesto.asignacion.controller;

import com.presupuesto.asignacion.dto.request.AsignarRequest;
import com.presupuesto.asignacion.dto.request.MoverDineroRequest;
import com.presupuesto.asignacion.dto.response.AsignacionActualizadaResponse;
import com.presupuesto.asignacion.dto.response.MesPresupuestoResponse;
import com.presupuesto.asignacion.service.AsignacionService;
import com.presupuesto.asignacion.service.MesPresupuestoService;
import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presupuestos/{presupuestoId}/meses/{mes}")
@RequiredArgsConstructor
public class AsignacionController {

    private final AsignacionService asignacionService;
    private final MesPresupuestoService mesService;

    @GetMapping
    public MesPresupuestoResponse obtener(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable String mes,
            @RequestParam(defaultValue = "false") boolean incluirOcultas) {
        return mesService.obtener(presupuestoId, usuario.id(), mes, incluirOcultas);
    }

    @PutMapping("/categorias/{categoriaId}")
    public AsignacionActualizadaResponse asignar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable String mes,
            @PathVariable Long categoriaId,
            @Valid @RequestBody AsignarRequest request) {
        return asignacionService.asignar(presupuestoId, usuario.id(), mes, categoriaId, request);
    }

    @PostMapping("/mover-dinero")
    public MesPresupuestoResponse moverDinero(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable String mes,
            @Valid @RequestBody MoverDineroRequest request) {
        return asignacionService.moverDinero(presupuestoId, usuario.id(), mes, request);
    }
}
