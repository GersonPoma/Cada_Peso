package com.presupuesto.reporte.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.reporte.repository.MovimientoMensualRepository.MovimientoPorMes;
import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import com.presupuesto.transaccion.repository.TransaccionRepository.SumaPorCuenta;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MovimientoMensualRepositoryTest {

    private static final LocalDate FIN_ANIO = LocalDate.of(2026, 12, 31);

    @Autowired
    private MovimientoMensualRepository repository;

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Presupuesto casa;
    private Presupuesto viajes;
    private Cuenta banco;
    private Cuenta fuera;
    private Cuenta cerrada;
    private Cuenta prestamo;
    private Cuenta cuentaViajes;

    @BeforeEach
    void crearDatos() {
        Usuario ana = usuarioRepository.saveAndFlush(
                Usuario.builder().email("ana-movimiento@ejemplo.com").contrasena("hash").build());
        casa = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Casa"));
        viajes = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Viajes"));
        banco = cuentaRepository.save(cuenta(casa, "Banco", TipoCuenta.CORRIENTE, true));
        fuera = cuentaRepository.save(cuenta(casa, "Fuera", TipoCuenta.INVERSION, false));
        cerrada = cuentaRepository.save(cuenta(casa, "Cerrada", TipoCuenta.EFECTIVO, true));
        cerrada.cerrar();
        prestamo = cuentaRepository.save(cuenta(casa, "Prestamo", TipoCuenta.PRESTAMO, false));
        cuentaViajes = cuentaRepository.saveAndFlush(
                cuenta(viajes, "Banco", TipoCuenta.CORRIENTE, true));
    }

    @Test
    void separaEntradasYSalidasPorCuentaYMesDeTodasLasCuentasDelPresupuesto() {
        guardar(banco, "2026-01-05", 500_000L);
        guardar(banco, "2026-01-06", -20_000L);
        guardar(banco, "2026-01-31", -5_000L);
        guardar(banco, "2026-02-01", 7_000L);
        guardar(fuera, "2026-01-10", 90_000L);
        guardar(cerrada, "2026-01-11", -1_000L);
        guardar(prestamo, "2026-03-01", 4_000L);
        guardar(cuentaViajes, "2026-01-05", 99_999L);

        List<MovimientoPorMes> filas = repository.movimientoPorCuentaYMes(casa.getId(), FIN_ANIO);

        assertThat(filas).hasSize(5);
        assertThat(entradas(filas, banco, 2026, 1)).isEqualTo(500_000L);
        assertThat(salidas(filas, banco, 2026, 1)).isEqualTo(-25_000L);
        assertThat(entradas(filas, banco, 2026, 2)).isEqualTo(7_000L);
        assertThat(salidas(filas, banco, 2026, 2)).isZero();
        assertThat(entradas(filas, fuera, 2026, 1)).isEqualTo(90_000L);
        assertThat(salidas(filas, cerrada, 2026, 1)).isEqualTo(-1_000L);
        assertThat(entradas(filas, prestamo, 2026, 3)).isEqualTo(4_000L);
    }

    @Test
    void respetaElTopeDeFechaConLaFronteraDeMes() {
        guardar(banco, "2026-10-31", -3_000L);
        guardar(banco, "2026-11-01", -5_000L);

        List<MovimientoPorMes> hastaOctubre =
                repository.movimientoPorCuentaYMes(casa.getId(), LocalDate.of(2026, 10, 31));
        List<MovimientoPorMes> hastaNoviembre =
                repository.movimientoPorCuentaYMes(casa.getId(), LocalDate.of(2026, 11, 30));

        assertThat(hastaOctubre).hasSize(1);
        assertThat(salidas(hastaOctubre, banco, 2026, 10)).isEqualTo(-3_000L);
        assertThat(hastaNoviembre).hasSize(2);
        assertThat(salidas(hastaNoviembre, banco, 2026, 11)).isEqualTo(-5_000L);
    }

    @Test
    void cuentaTodosLosEstadosDeTransaccion() {
        Transaccion conciliada = guardar(banco, "2026-01-05", 10_000L);
        conciliada.cambiarEstado(EstadoTransaccion.CONCILIADA);
        Transaccion reconciliada = guardar(banco, "2026-01-06", 20_000L);
        reconciliada.cambiarEstado(EstadoTransaccion.RECONCILIADA);
        guardar(banco, "2026-01-07", 40_000L);
        transaccionRepository.flush();

        List<MovimientoPorMes> filas = repository.movimientoPorCuentaYMes(casa.getId(), FIN_ANIO);

        assertThat(entradas(filas, banco, 2026, 1)).isEqualTo(70_000L);
    }

    @Test
    void lasSumasPorMesIgualanLaSumaTotalPorCuentaDelListadoDeSaldos() {
        guardar(banco, "2025-12-31", 300_000L);
        guardar(banco, "2026-01-05", -20_000L);
        guardar(banco, "2026-06-05", 7_000L);
        guardar(fuera, "2026-02-10", 90_000L);
        guardar(cerrada, "2026-03-11", -1_000L);
        transaccionRepository.flush();

        List<MovimientoPorMes> filas = repository.movimientoPorCuentaYMes(
                casa.getId(), LocalDate.of(9999, 12, 31));

        for (SumaPorCuenta suma : transaccionRepository.sumarPorCuenta(casa.getId())) {
            long porMes = filas.stream()
                    .filter(f -> f.getCuentaId().equals(suma.getCuentaId()))
                    .mapToLong(f -> f.getEntradas() + f.getSalidas())
                    .sum();
            assertThat(porMes).as("cuenta %d", suma.getCuentaId()).isEqualTo(suma.getTotal());
        }
    }

    @Test
    void elMovimientoDeUnaCuentaSoloTraeEsaCuentaHastaElTope() {
        guardar(banco, "2026-01-05", 500_000L);
        guardar(banco, "2026-01-06", -20_000L);
        guardar(banco, "2026-05-06", -1_000L);
        guardar(fuera, "2026-01-10", 90_000L);

        List<MovimientoPorMes> filas =
                repository.movimientoDeCuentaPorMes(banco.getId(), LocalDate.of(2026, 3, 31));

        assertThat(filas).hasSize(1);
        assertThat(entradas(filas, banco, 2026, 1)).isEqualTo(500_000L);
        assertThat(salidas(filas, banco, 2026, 1)).isEqualTo(-20_000L);
        assertThat(repository.movimientoDeCuentaPorMes(cuentaViajes.getId(), FIN_ANIO)).isEmpty();
    }

    private static long entradas(List<MovimientoPorMes> filas, Cuenta cuenta, int anio, int mes) {
        return filas.stream()
                .filter(f -> f.getCuentaId().equals(cuenta.getId())
                        && f.getAnio() == anio && f.getMes() == mes)
                .mapToLong(MovimientoPorMes::getEntradas)
                .sum();
    }

    private static long salidas(List<MovimientoPorMes> filas, Cuenta cuenta, int anio, int mes) {
        return filas.stream()
                .filter(f -> f.getCuentaId().equals(cuenta.getId())
                        && f.getAnio() == anio && f.getMes() == mes)
                .mapToLong(MovimientoPorMes::getSalidas)
                .sum();
    }

    private Transaccion guardar(Cuenta cuenta, String fecha, long monto) {
        return transaccionRepository.saveAndFlush(Transaccion.builder()
                .cuenta(cuenta)
                .fecha(LocalDate.parse(fecha))
                .monto(monto)
                .build());
    }

    private static Cuenta cuenta(
            Presupuesto presupuesto, String nombre, TipoCuenta tipo, boolean enPresupuesto) {
        return Cuenta.builder()
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(Cuenta.normalizar(nombre))
                .tipo(tipo)
                .enPresupuesto(enPresupuesto)
                .saldoInicial(0L)
                .build();
    }

    private static Presupuesto nuevoPresupuesto(Usuario usuario, String nombre) {
        return Presupuesto.builder()
                .usuario(usuario)
                .nombre(nombre)
                .nombreNormalizado(Presupuesto.normalizar(nombre))
                .moneda("BOB")
                .build();
    }
}
