package com.presupuesto.transaccion.controller;

import com.presupuesto.comun.paginacion.PaginaResponse;
import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import com.presupuesto.transaccion.dto.request.ActualizarTransaccionRequest;
import com.presupuesto.transaccion.dto.request.CambiarEstadoRequest;
import com.presupuesto.transaccion.dto.request.CrearTransaccionRequest;
import com.presupuesto.transaccion.dto.request.FiltroTransacciones;
import com.presupuesto.transaccion.dto.request.LoteRequest;
import com.presupuesto.transaccion.dto.request.MoverCuentaRequest;
import com.presupuesto.transaccion.dto.response.LoteResponse;
import com.presupuesto.transaccion.dto.response.SaldoCuentaResponse;
import com.presupuesto.transaccion.dto.response.TransaccionResponse;
import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.service.SaldoCuentaService;
import com.presupuesto.transaccion.service.TransaccionLoteService;
import com.presupuesto.transaccion.service.TransaccionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presupuestos/{presupuestoId}/transacciones")
@RequiredArgsConstructor
public class TransaccionController {

    private final TransaccionService transaccionService;
    private final TransaccionLoteService loteService;
    private final SaldoCuentaService saldoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransaccionResponse crear(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @Valid @RequestBody CrearTransaccionRequest request) {
        return transaccionService.crear(presupuestoId, usuario.id(), request);
    }

    @GetMapping
    public PaginaResponse<TransaccionResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TransaccionService.TAMANO_POR_DEFECTO) int size,
            @RequestParam(required = false) Long cuentaId,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @RequestParam(required = false) EstadoTransaccion estado,
            @RequestParam(defaultValue = "false") boolean soloSinAprobar,
            @RequestParam(required = false) String q) {
        FiltroTransacciones filtro = new FiltroTransacciones(
                cuentaId, categoriaId, desde, hasta, estado, soloSinAprobar, q);
        return transaccionService.listar(presupuestoId, usuario.id(), filtro, page, size);
    }

    @GetMapping("/saldos")
    public List<SaldoCuentaResponse> saldos(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId) {
        return saldoService.listar(presupuestoId, usuario.id());
    }

    @PostMapping("/lote")
    public LoteResponse lote(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @Valid @RequestBody LoteRequest request) {
        return loteService.ejecutar(presupuestoId, usuario.id(), request);
    }

    @GetMapping("/{id}")
    public TransaccionResponse obtener(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return transaccionService.obtener(presupuestoId, usuario.id(), id);
    }

    @PutMapping("/{id}")
    public TransaccionResponse actualizar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id,
            @Valid @RequestBody ActualizarTransaccionRequest request) {
        return transaccionService.actualizar(presupuestoId, usuario.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        transaccionService.borrar(presupuestoId, usuario.id(), id);
    }

    @PostMapping("/{id}/aprobar")
    public TransaccionResponse aprobar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return transaccionService.aprobar(presupuestoId, usuario.id(), id);
    }

    @PutMapping("/{id}/estado")
    public TransaccionResponse cambiarEstado(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id,
            @Valid @RequestBody CambiarEstadoRequest request) {
        return transaccionService.cambiarEstado(presupuestoId, usuario.id(), id, request);
    }

    @PostMapping("/{id}/mover-cuenta")
    public TransaccionResponse moverCuenta(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id,
            @Valid @RequestBody MoverCuentaRequest request) {
        return transaccionService.moverCuenta(presupuestoId, usuario.id(), id, request);
    }

    @PostMapping("/{id}/duplicar")
    @ResponseStatus(HttpStatus.CREATED)
    public TransaccionResponse duplicar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return transaccionService.duplicar(presupuestoId, usuario.id(), id);
    }
}
