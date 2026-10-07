package com.presupuesto.transaccion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.SubTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TransaccionRepositoryTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 10, 2);

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EntityManager entityManager;

    private Presupuesto casa;
    private Presupuesto viajes;
    private Cuenta banco;
    private Cuenta efectivo;
    private Cuenta enViajes;

    @BeforeEach
    void crearDatos() {
        Usuario ana = usuarioRepository.saveAndFlush(
                Usuario.builder().email("ana-transaccion@ejemplo.com").contrasena("hash").build());
        casa = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Casa"));
        viajes = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Viajes"));
        banco = cuentaRepository.saveAndFlush(nuevaCuenta(casa, "Banco", 100_000L));
        efectivo = cuentaRepository.saveAndFlush(nuevaCuenta(casa, "Efectivo", 0L));
        enViajes = cuentaRepository.saveAndFlush(nuevaCuenta(viajes, "Banco", 50_000L));
    }

    @Test
    void findByIdAndCuentaPresupuestoIdSoloEncuentraLaDeEsePresupuesto() {
        Transaccion guardada = transaccionRepository.saveAndFlush(nueva(banco, -1000L));

        assertThat(transaccionRepository
                        .findByIdAndCuentaPresupuestoId(guardada.getId(), casa.getId()))
                .isPresent();
        assertThat(transaccionRepository
                        .findByIdAndCuentaPresupuestoId(guardada.getId(), viajes.getId()))
                .isEmpty();
        assertThat(transaccionRepository
                        .findByIdAndCuentaPresupuestoId(Long.MAX_VALUE, casa.getId()))
                .isEmpty();
    }

    @Test
    void findByIdInAndCuentaPresupuestoIdDejaAfueraLasAjenas() {
        Transaccion propia = transaccionRepository.save(nueva(banco, -1000L));
        Transaccion ajena = transaccionRepository.saveAndFlush(nueva(enViajes, -2000L));

        List<Transaccion> halladas = transaccionRepository.findByIdInAndCuentaPresupuestoId(
                List.of(propia.getId(), ajena.getId()), casa.getId());

        assertThat(halladas).containsExactly(propia);
    }

    @Test
    void guardarConSubtransaccionesLasPersiste() {
        Transaccion dividida = nueva(banco, -3000L);
        dividida.reemplazarSubtransacciones(List.of(sub(-1000L), sub(-2000L)));

        transaccionRepository.saveAndFlush(dividida);
        entityManager.clear();

        Transaccion leida = transaccionRepository.findById(dividida.getId()).orElseThrow();
        assertThat(leida.getSubtransacciones()).extracting(SubTransaccion::getMonto)
                .containsExactly(-1000L, -2000L);
    }

    @Test
    void reemplazarLasSubtransaccionesBorraLasHuerfanas() {
        Transaccion dividida = nueva(banco, -3000L);
        dividida.reemplazarSubtransacciones(List.of(sub(-1000L), sub(-2000L)));
        transaccionRepository.saveAndFlush(dividida);

        dividida.reemplazarSubtransacciones(List.of(sub(-1500L), sub(-1500L)));
        transaccionRepository.saveAndFlush(dividida);
        entityManager.clear();

        assertThat(contarSubtransacciones()).isEqualTo(2);
    }

    @Test
    void borrarLaTransaccionBorraSusSubtransacciones() {
        Transaccion dividida = nueva(banco, -3000L);
        dividida.reemplazarSubtransacciones(List.of(sub(-1000L), sub(-2000L)));
        transaccionRepository.saveAndFlush(dividida);

        transaccionRepository.delete(dividida);
        transaccionRepository.flush();

        assertThat(contarSubtransacciones()).isZero();
        assertThat(transaccionRepository.findById(dividida.getId())).isEmpty();
    }

    @Test
    void sumarPorCuentaSeparaTotalYConciliadoYSeAcotaPorPresupuesto() {
        transaccionRepository.save(nueva(banco, -20_000L));
        transaccionRepository.save(conEstado(banco, -10_000L, EstadoTransaccion.CONCILIADA));
        transaccionRepository.save(conEstado(banco, 5_000L, EstadoTransaccion.RECONCILIADA));
        transaccionRepository.save(nueva(efectivo, 700L));
        transaccionRepository.saveAndFlush(nueva(enViajes, -9_999L));

        Map<Long, TransaccionRepository.SumaPorCuenta> sumas = transaccionRepository
                .sumarPorCuenta(casa.getId()).stream()
                .collect(Collectors.toMap(
                        TransaccionRepository.SumaPorCuenta::getCuentaId, Function.identity()));

        assertThat(sumas).containsOnlyKeys(banco.getId(), efectivo.getId());
        assertThat(sumas.get(banco.getId()).getTotal()).isEqualTo(-25_000L);
        assertThat(sumas.get(banco.getId()).getConciliado()).isEqualTo(-5_000L);
        assertThat(sumas.get(efectivo.getId()).getTotal()).isEqualTo(700L);
        assertThat(sumas.get(efectivo.getId()).getConciliado()).isZero();
    }

    @Test
    void sumarPorCuentaSinTransaccionesDevuelveVacio() {
        assertThat(transaccionRepository.sumarPorCuenta(casa.getId())).isEmpty();
    }

    /** Solo las del presupuesto del test: la base puede tener datos de otras personas. */
    private long contarSubtransacciones() {
        return entityManager
                .createQuery("select count(s) from SubTransaccion s "
                        + "where s.transaccion.cuenta.presupuesto.id = :presupuestoId", Long.class)
                .setParameter("presupuestoId", casa.getId())
                .getSingleResult();
    }

    private static Presupuesto nuevoPresupuesto(Usuario usuario, String nombre) {
        return Presupuesto.builder()
                .usuario(usuario)
                .nombre(nombre)
                .nombreNormalizado(Presupuesto.normalizar(nombre))
                .moneda("BOB")
                .build();
    }

    private static Cuenta nuevaCuenta(Presupuesto presupuesto, String nombre, long saldoInicial) {
        return Cuenta.builder()
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(Cuenta.normalizar(nombre))
                .tipo(TipoCuenta.CORRIENTE)
                .saldoInicial(saldoInicial)
                .build();
    }

    private static Transaccion nueva(Cuenta cuenta, long monto) {
        return Transaccion.builder().cuenta(cuenta).fecha(FECHA).monto(monto).build();
    }

    private static Transaccion conEstado(Cuenta cuenta, long monto, EstadoTransaccion estado) {
        return Transaccion.builder()
                .cuenta(cuenta).fecha(FECHA).monto(monto).estado(estado).build();
    }

    private static SubTransaccion sub(long monto) {
        return SubTransaccion.builder().monto(monto).build();
    }
}
