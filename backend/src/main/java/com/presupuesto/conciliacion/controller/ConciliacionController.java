package com.presupuesto.conciliacion.controller;

import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import com.presupuesto.conciliacion.dto.request.CrearConciliacionRequest;
import com.presupuesto.conciliacion.dto.response.ConciliacionResponse;
import com.presupuesto.conciliacion.dto.response.EstadoConciliacionResponse;
import com.presupuesto.conciliacion.service.ConciliacionService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presupuestos/{presupuestoId}/cuentas/{cuentaId}/conciliacion")
@RequiredArgsConstructor
public class ConciliacionController {

    private final ConciliacionService conciliacionService;

    /** Los parámetros son opcionales aquí para que el service responda 400 tras los 404. */
    @GetMapping("/estado")
    public EstadoConciliacionResponse estado(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long cuentaId,
            @RequestParam(required = false) Long saldoExtracto,
            @RequestParam(required = false) LocalDate fecha) {
        return conciliacionService.estado(
                presupuestoId, usuario.id(), cuentaId, saldoExtracto, fecha);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConciliacionResponse crear(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long cuentaId,
            @RequestBody CrearConciliacionRequest request) {
        return conciliacionService.crear(presupuestoId, usuario.id(), cuentaId, request);
    }

    @GetMapping
    public List<ConciliacionResponse> historial(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long cuentaId) {
        return conciliacionService.historial(presupuestoId, usuario.id(), cuentaId);
    }
}
