package com.presupuesto.reporte.controller;

import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import com.presupuesto.reporte.dto.response.CumplimientoMetasResponse;
import com.presupuesto.reporte.dto.response.EvolucionSaldoResponse;
import com.presupuesto.reporte.dto.response.GastoPorCategoriaResponse;
import com.presupuesto.reporte.dto.response.IngresosGastosResponse;
import com.presupuesto.reporte.dto.response.PatrimonioResponse;
import com.presupuesto.reporte.service.GastoReporteService;
import com.presupuesto.reporte.service.MetasReporteService;
import com.presupuesto.reporte.service.PatrimonioReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reportes de solo lectura. Los meses llegan como texto y se validan en el service: así un
 * presupuesto o una cuenta inexistente responde 404 antes que un mes mal formado (400).
 */
@RestController
@RequestMapping("/api/v1/presupuestos/{presupuestoId}/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final GastoReporteService gastoService;
    private final PatrimonioReporteService patrimonioService;
    private final MetasReporteService metasService;

    @GetMapping("/gasto-por-categoria")
    public GastoPorCategoriaResponse gastoPorCategoria(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        return gastoService.gastoPorCategoria(presupuestoId, usuario.id(), desde, hasta);
    }

    @GetMapping("/ingresos-gastos")
    public IngresosGastosResponse ingresosGastos(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        return gastoService.ingresosGastos(presupuestoId, usuario.id(), desde, hasta);
    }

    @GetMapping("/patrimonio")
    public PatrimonioResponse patrimonio(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        return patrimonioService.patrimonio(presupuestoId, usuario.id(), desde, hasta);
    }

    @GetMapping("/cuentas/{cuentaId}/evolucion-saldo")
    public EvolucionSaldoResponse evolucionSaldo(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long cuentaId,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        return patrimonioService.evolucionSaldo(
                presupuestoId, usuario.id(), cuentaId, desde, hasta);
    }

    /** {@code hasta} es opcional: sin él, el reporte trae solo el mes de {@code desde}. */
    @GetMapping("/metas")
    public CumplimientoMetasResponse metas(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        return metasService.cumplimiento(presupuestoId, usuario.id(), desde, hasta);
    }
}
