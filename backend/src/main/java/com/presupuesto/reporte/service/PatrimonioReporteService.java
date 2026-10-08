package com.presupuesto.reporte.service;

import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.reporte.dto.response.EvolucionSaldoResponse;
import com.presupuesto.reporte.dto.response.MesPatrimonioResponse;
import com.presupuesto.reporte.dto.response.MesSaldoResponse;
import com.presupuesto.reporte.dto.response.PatrimonioResponse;
import com.presupuesto.reporte.repository.MovimientoMensualRepository;
import com.presupuesto.reporte.repository.MovimientoMensualRepository.MovimientoPorMes;
import com.presupuesto.reporte.service.CalculoPatrimonio.CuentaConSaldos;
import com.presupuesto.reporte.service.CalculoSaldos.Movimiento;
import com.presupuesto.reporte.service.CalculoSaldos.SaldoMes;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patrimonio neto y evolución del saldo de una cuenta. Orden de errores: presupuesto 404, cuenta
 * 404 (solo la evolución) y parámetros 400. Una consulta de movimientos por reporte, sin una por
 * mes ni por cuenta.
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(ReporteProperties.class)
public class PatrimonioReporteService {

    static final String MENSAJE_CUENTA_NO_ENCONTRADA = "Cuenta no encontrada";

    private final PresupuestoService presupuestoService;
    private final CuentaRepository cuentaRepository;
    private final MovimientoMensualRepository movimientoRepository;
    private final ReporteProperties propiedades;

    /** 3 consultas: presupuesto, cuentas y movimientos por cuenta y mes. */
    @Transactional(readOnly = true)
    public PatrimonioResponse patrimonio(
            Long presupuestoId, Long usuarioId, String desde, String hasta) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        RangoMeses rango = RangoMeses.interpretar(desde, hasta, propiedades.maxMeses(), false);
        List<Cuenta> cuentas =
                cuentaRepository.findByPresupuestoIdOrderByNombreNormalizado(presupuestoId);
        Map<Long, Map<YearMonth, Movimiento>> movimientos = new HashMap<>();
        for (MovimientoPorMes fila : movimientoRepository.movimientoPorCuentaYMes(
                presupuestoId, rango.hasta().atEndOfMonth())) {
            movimientos.computeIfAbsent(fila.getCuentaId(), clave -> new HashMap<>())
                    .put(YearMonth.of(fila.getAnio(), fila.getMes()), movimiento(fila));
        }
        List<CuentaConSaldos> conSaldos = new ArrayList<>();
        for (Cuenta cuenta : cuentas) {
            conSaldos.add(new CuentaConSaldos(
                    cuenta.getTipo(),
                    CalculoSaldos.alCierre(
                            cuenta.getSaldoInicial(),
                            movimientos.getOrDefault(cuenta.getId(), Map.of()),
                            rango)));
        }
        List<MesPatrimonioResponse> meses = CalculoPatrimonio.calcular(conSaldos, rango).stream()
                .map(mes -> MesPatrimonioResponse.desde(mes.mes(), mes.activos(), mes.pasivos()))
                .toList();
        return PatrimonioResponse.desde(rango.desde(), rango.hasta(), meses);
    }

    /** 3 consultas: presupuesto, cuenta y movimientos de la cuenta por mes. */
    @Transactional(readOnly = true)
    public EvolucionSaldoResponse evolucionSaldo(
            Long presupuestoId, Long usuarioId, Long cuentaId, String desde, String hasta) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        Cuenta cuenta = cuentaRepository.findByIdAndPresupuestoId(cuentaId, presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_CUENTA_NO_ENCONTRADA));
        RangoMeses rango = RangoMeses.interpretar(desde, hasta, propiedades.maxMeses(), false);
        Map<YearMonth, Movimiento> movimientos = new HashMap<>();
        for (MovimientoPorMes fila : movimientoRepository.movimientoDeCuentaPorMes(
                cuenta.getId(), rango.hasta().atEndOfMonth())) {
            movimientos.put(YearMonth.of(fila.getAnio(), fila.getMes()), movimiento(fila));
        }
        List<MesSaldoResponse> meses =
                CalculoSaldos.alCierre(cuenta.getSaldoInicial(), movimientos, rango).stream()
                        .map(PatrimonioReporteService::mesSaldo)
                        .toList();
        return EvolucionSaldoResponse.desde(cuenta, rango.desde(), rango.hasta(), meses);
    }

    private static Movimiento movimiento(MovimientoPorMes fila) {
        return new Movimiento(fila.getEntradas(), fila.getSalidas());
    }

    private static MesSaldoResponse mesSaldo(SaldoMes saldo) {
        return MesSaldoResponse.desde(
                saldo.mes(), saldo.entradas(), saldo.salidas(), saldo.saldo());
    }
}
