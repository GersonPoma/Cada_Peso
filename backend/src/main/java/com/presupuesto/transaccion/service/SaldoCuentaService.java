package com.presupuesto.transaccion.service;

import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.dto.response.SaldoCuentaResponse;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import com.presupuesto.transaccion.repository.TransaccionRepository.SumaPorCuenta;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saldo de cada cuenta del presupuesto (abiertas y cerradas), calculado en la base de datos. */
@Service
@RequiredArgsConstructor
public class SaldoCuentaService {

    /** Fecha tope para el saldo conciliado total. */
    public static final LocalDate SIN_LIMITE = LocalDate.of(9999, 12, 31);

    private final TransaccionRepository transaccionRepository;
    private final CuentaRepository cuentaRepository;
    private final PresupuestoService presupuestoService;

    @Transactional(readOnly = true)
    public List<SaldoCuentaResponse> listar(Long presupuestoId, Long usuarioId) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        Map<Long, SumaPorCuenta> sumas = transaccionRepository.sumarPorCuenta(presupuestoId)
                .stream()
                .collect(Collectors.toMap(SumaPorCuenta::getCuentaId, Function.identity()));
        return cuentaRepository.findByPresupuestoIdOrderByNombreNormalizado(presupuestoId)
                .stream()
                .map(cuenta -> saldoDe(cuenta, sumas.get(cuenta.getId())))
                .toList();
    }

    /**
     * Saldo conciliado de la cuenta a una fecha: saldo inicial más las {@code CONCILIADA} y
     * {@code RECONCILIADA} con fecha menor o igual a {@code hasta}. Mismo criterio que
     * {@link #listar}.
     */
    @Transactional(readOnly = true)
    public long saldoConciliadoAl(Cuenta cuenta, LocalDate hasta) {
        return cuenta.getSaldoInicial()
                + transaccionRepository.sumaConciliadaDeCuenta(cuenta.getId(), hasta);
    }

    private static SaldoCuentaResponse saldoDe(Cuenta cuenta, SumaPorCuenta suma) {
        return suma == null
                ? SaldoCuentaResponse.desde(cuenta, 0L, 0L)
                : SaldoCuentaResponse.desde(cuenta, suma.getTotal(), suma.getConciliado());
    }
}
