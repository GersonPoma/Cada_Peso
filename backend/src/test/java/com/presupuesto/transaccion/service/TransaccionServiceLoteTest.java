package com.presupuesto.transaccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.beneficiario.entity.Beneficiario;
import com.presupuesto.beneficiario.repository.BeneficiarioRepository;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.transaccion.dto.request.CrearTransaccionRequest;
import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** {@code crearLote} y {@code contarExistentesPorClave}, contra la base real. */
@SpringBootTest
@Transactional
class TransaccionServiceLoteTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 3, 5);

    @Autowired
    private TransaccionService service;

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private BeneficiarioRepository beneficiarioRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Presupuesto presupuesto;
    private Cuenta banco;
    private Cuenta otra;

    @BeforeEach
    void crearDatos() {
        Usuario ana = usuarioRepository.saveAndFlush(Usuario.builder()
                .email("lote-" + UUID.randomUUID() + "@ejemplo.com").contrasena("hash").build());
        presupuesto = presupuestoRepository.saveAndFlush(Presupuesto.builder()
                .usuario(ana).nombre("Casa").nombreNormalizado("casa").moneda("BOB").build());
        banco = cuenta("Banco", false);
        otra = cuenta("Otra", false);
    }

    private Cuenta cuenta(String nombre, boolean cerrada) {
        return cuentaRepository.saveAndFlush(Cuenta.builder()
                .presupuesto(presupuesto).nombre(nombre)
                .nombreNormalizado(nombre.toLowerCase()).tipo(TipoCuenta.CORRIENTE)
                .cerrada(cerrada).build());
    }

    private CrearTransaccionRequest fila(
            Cuenta cuenta, LocalDate fecha, long monto, String beneficiario) {
        return new CrearTransaccionRequest(
                cuenta.getId(), fecha, monto, null, beneficiario, null, false, null);
    }

    private long beneficiariosDelPresupuesto() {
        return beneficiarioRepository
                .findByPresupuestoIdOrderByNombreNormalizado(presupuesto.getId()).size();
    }

    @Test
    void contarSumaMayusculasYMinusculasDelMismoBeneficiario() {
        service.crearLote(presupuesto, banco, List.of(
                fila(banco, FECHA, -4500, "Cafe Luna"),
                fila(banco, FECHA, -4500, "CAFE LUNA"),
                fila(banco, FECHA, -4500, null)));

        Map<ClaveMovimiento, Integer> conteo =
                service.contarExistentesPorClave(banco.getId(), FECHA, FECHA);

        assertThat(conteo)
                .containsEntry(ClaveMovimiento.de(FECHA, -4500, "cafe luna"), 2)
                .containsEntry(ClaveMovimiento.de(FECHA, -4500, null), 1)
                .hasSize(2);
    }

    @Test
    void contarIncluyeTodosLosEstadosIgnoraOtraCuentaYFechasFueraDelRango() {
        List<Transaccion> creadas = service.crearLote(presupuesto, banco, List.of(
                fila(banco, FECHA, -100, "A"),
                fila(banco, FECHA.plusDays(10), -100, "A")));
        creadas.get(0).cambiarEstado(EstadoTransaccion.RECONCILIADA);
        transaccionRepository.saveAndFlush(creadas.get(0));
        service.crearLote(presupuesto, otra, List.of(fila(otra, FECHA, -100, "A")));

        Map<ClaveMovimiento, Integer> conteo =
                service.contarExistentesPorClave(banco.getId(), FECHA, FECHA.plusDays(5));

        assertThat(conteo).containsExactly(Map.entry(ClaveMovimiento.de(FECHA, -100, "A"), 1));
    }

    @Test
    void crearLoteCreaNoConciliadasSinAprobarYSinCategoria() {
        List<Transaccion> creadas = service.crearLote(presupuesto, banco, List.of(
                fila(banco, FECHA, -4500, "Cafe Luna"), fila(banco, FECHA, 9000, null)));

        assertThat(creadas).hasSize(2).allSatisfy(t -> {
            assertThat(t.getId()).isNotNull();
            assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.NO_CONCILIADA);
            assertThat(t.isAprobada()).isFalse();
            assertThat(t.getCategoria()).isNull();
            assertThat(t.getSubtransacciones()).isEmpty();
            assertThat(t.getCuenta().getId()).isEqualTo(banco.getId());
        });
        assertThat(creadas.get(1).getBeneficiario()).isNull();
    }

    @Test
    void crearLoteCreaElBeneficiarioUnaVezYLoReutilizaSinDistinguirMayusculas() {
        Beneficiario existente = beneficiarioRepository.saveAndFlush(Beneficiario.builder()
                .presupuesto(presupuesto).nombre("Cafe Luna").nombreNormalizado("cafe luna")
                .build());

        List<Transaccion> creadas = service.crearLote(presupuesto, banco, List.of(
                fila(banco, FECHA, -1, "CAFE LUNA"),
                fila(banco, FECHA.plusDays(1), -2, "Panaderia"),
                fila(banco, FECHA.plusDays(2), -3, "PANADERIA")));

        assertThat(beneficiariosDelPresupuesto()).isEqualTo(2);
        assertThat(creadas.get(0).getBeneficiarioVinculado().getId())
                .isEqualTo(existente.getId());
        assertThat(creadas.get(0).getBeneficiario()).isEqualTo("Cafe Luna");
        assertThat(creadas.get(2).getBeneficiarioVinculado())
                .isSameAs(creadas.get(1).getBeneficiarioVinculado());
    }

    @Test
    void crearLoteEnCuentaCerradaResponde422YNoCreaNada() {
        Cuenta cerrada = cuenta("Cerrada", true);
        long antes = transaccionRepository.count();

        assertThrows(ReglaNegocioException.class, () -> service.crearLote(
                presupuesto, cerrada, List.of(fila(cerrada, FECHA, -1, "Nuevo"))));

        assertThat(transaccionRepository.count()).isEqualTo(antes);
        assertThat(beneficiariosDelPresupuesto()).isZero();
    }

    @Test
    void siUnaFilaFallaSePropagaElErrorParaQueLaTransaccionSeRevierta() {
        CrearTransaccionRequest conCategoriaAjena = new CrearTransaccionRequest(
                banco.getId(), FECHA, -1L, 999_999_999L, "X", null, false, null);

        assertThrows(RuntimeException.class, () -> service.crearLote(presupuesto, banco, List.of(
                fila(banco, FECHA, -5, "Bueno"), conCategoriaAjena)));
    }
}
