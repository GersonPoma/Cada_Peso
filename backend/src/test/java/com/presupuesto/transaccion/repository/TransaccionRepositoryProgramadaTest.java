package com.presupuesto.transaccion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TransaccionRepositoryProgramadaTest {

    private static final LocalDate OCURRENCIA = LocalDate.of(2026, 9, 5);
    private static final long PROGRAMADA_ID = 987_654L;

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Cuenta banco;

    @BeforeEach
    void crearDatos() {
        Usuario ana = usuarioRepository.saveAndFlush(Usuario.builder()
                .email("ana-programada-repo@ejemplo.com").contrasena("hash").build());
        Presupuesto casa = presupuestoRepository.saveAndFlush(Presupuesto.builder()
                .usuario(ana).nombre("Casa").nombreNormalizado("casa").moneda("BOB").build());
        banco = cuentaRepository.saveAndFlush(Cuenta.builder()
                .presupuesto(casa).nombre("Banco").nombreNormalizado("banco")
                .tipo(TipoCuenta.CORRIENTE).build());
    }

    private Transaccion nueva(Long programadaId, LocalDate ocurrencia) {
        return Transaccion.builder()
                .cuenta(banco).fecha(OCURRENCIA).monto(-1000L)
                .programadaId(programadaId).fechaOcurrencia(ocurrencia).build();
    }

    @Test
    void existsPorPlantillaYFechaDeOcurrencia() {
        transaccionRepository.saveAndFlush(nueva(PROGRAMADA_ID, OCURRENCIA));

        assertThat(transaccionRepository
                .existsByProgramadaIdAndFechaOcurrencia(PROGRAMADA_ID, OCURRENCIA)).isTrue();
        assertThat(transaccionRepository.existsByProgramadaIdAndFechaOcurrencia(
                PROGRAMADA_ID, OCURRENCIA.plusDays(1))).isFalse();
        assertThat(transaccionRepository
                .existsByProgramadaIdAndFechaOcurrencia(PROGRAMADA_ID + 1, OCURRENCIA)).isFalse();
    }

    @Test
    void laRestriccionUnicaImpideDuplicarLaOcurrenciaDeUnaPlantilla() {
        transaccionRepository.saveAndFlush(nueva(PROGRAMADA_ID, OCURRENCIA));

        assertThatThrownBy(() -> transaccionRepository.saveAndFlush(
                nueva(PROGRAMADA_ID, OCURRENCIA)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lasTransaccionesSinPlantillaNoColisionanEntreSi() {
        transaccionRepository.saveAndFlush(nueva(null, null));
        transaccionRepository.saveAndFlush(nueva(null, null));

        assertThat(transaccionRepository.count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void desvincularProgramadaDejaElVinculoNuloYLiberaLaFecha() {
        Transaccion uno = transaccionRepository.saveAndFlush(nueva(PROGRAMADA_ID, OCURRENCIA));
        Transaccion otra = transaccionRepository.saveAndFlush(nueva(PROGRAMADA_ID + 1, OCURRENCIA));

        int afectadas = transaccionRepository.desvincularProgramada(PROGRAMADA_ID);

        assertThat(afectadas).isEqualTo(1);
        Transaccion recargada = transaccionRepository.findById(uno.getId()).orElseThrow();
        assertThat(recargada.getProgramadaId()).isNull();
        assertThat(recargada.getFechaOcurrencia()).isNull();
        assertThat(transaccionRepository.findById(otra.getId()).orElseThrow().getProgramadaId())
                .isEqualTo(PROGRAMADA_ID + 1);
        // La fecha quedó libre: otra transacción de la misma plantilla puede usarla.
        transaccionRepository.saveAndFlush(nueva(PROGRAMADA_ID, OCURRENCIA));
    }
}
