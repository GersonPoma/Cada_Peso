package com.presupuesto.cuenta.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CuentaRepositoryTest {

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Presupuesto casa;
    private Presupuesto viajes;

    @BeforeEach
    void crearPresupuestos() {
        Usuario ana = usuarioRepository.saveAndFlush(
                Usuario.builder().email("ana-cuenta@ejemplo.com").contrasena("hash").build());
        casa = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Casa"));
        viajes = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Viajes"));
    }

    @Test
    void findByIdAndPresupuestoIdSoloEncuentraLaCuentaDeEsePresupuesto() {
        Cuenta banco = cuentaRepository.saveAndFlush(nueva(casa, "Banco"));

        assertThat(cuentaRepository.findByIdAndPresupuestoId(banco.getId(), casa.getId()))
                .isPresent();
        assertThat(cuentaRepository.findByIdAndPresupuestoId(banco.getId(), viajes.getId()))
                .isEmpty();
        assertThat(cuentaRepository.findByIdAndPresupuestoId(Long.MAX_VALUE, casa.getId()))
                .isEmpty();
    }

    @Test
    void laListaEsDelPresupuestoYEstaOrdenadaSinDistinguirMayusculas() {
        cuentaRepository.save(nueva(casa, "Viajes"));
        cuentaRepository.save(nueva(casa, "banco"));
        cuentaRepository.save(nueva(casa, "Ahorros"));
        cuentaRepository.saveAndFlush(nueva(viajes, "Otra"));

        assertThat(cuentaRepository.findByPresupuestoIdOrderByNombreNormalizado(casa.getId()))
                .extracting(Cuenta::getNombre)
                .containsExactly("Ahorros", "banco", "Viajes");
    }

    @Test
    void laListaDeAbiertasOmiteLasCerradas() {
        cuentaRepository.save(nueva(casa, "Banco"));
        Cuenta vieja = nueva(casa, "Antigua");
        vieja.cerrar();
        cuentaRepository.saveAndFlush(vieja);

        assertThat(cuentaRepository
                .findByPresupuestoIdAndCerradaFalseOrderByNombreNormalizado(casa.getId()))
                .extracting(Cuenta::getNombre)
                .containsExactly("Banco");
        assertThat(cuentaRepository.findByPresupuestoIdOrderByNombreNormalizado(casa.getId()))
                .extracting(Cuenta::getNombre)
                .containsExactly("Antigua", "Banco");
    }

    @Test
    void existsPorNombreNormalizadoRespetaAlPresupuestoYExcluyeElPropioId() {
        Cuenta banco = cuentaRepository.saveAndFlush(nueva(casa, "Banco"));

        assertThat(cuentaRepository.existsByPresupuestoIdAndNombreNormalizado(
                casa.getId(), "banco")).isTrue();
        assertThat(cuentaRepository.existsByPresupuestoIdAndNombreNormalizado(
                viajes.getId(), "banco")).isFalse();
        assertThat(cuentaRepository.existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
                casa.getId(), "banco", banco.getId())).isFalse();
        assertThat(cuentaRepository.existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
                casa.getId(), "banco", Long.MAX_VALUE)).isTrue();
    }

    @Test
    void laRestriccionUnicaRechazaElMismoNombreNormalizadoDelMismoPresupuesto() {
        cuentaRepository.saveAndFlush(nueva(casa, "Banco"));

        assertThrows(DataIntegrityViolationException.class,
                () -> cuentaRepository.saveAndFlush(nueva(casa, "BANCO")));
    }

    @Test
    void laRestriccionUnicaPermiteElMismoNombreEnOtroPresupuesto() {
        cuentaRepository.saveAndFlush(nueva(casa, "Banco"));

        Cuenta enViajes = cuentaRepository.saveAndFlush(nueva(viajes, "Banco"));

        assertThat(enViajes.getId()).isNotNull();
    }

    private static Presupuesto nuevoPresupuesto(Usuario usuario, String nombre) {
        return Presupuesto.builder()
                .usuario(usuario)
                .nombre(nombre)
                .nombreNormalizado(Presupuesto.normalizar(nombre))
                .moneda("BOB")
                .build();
    }

    private static Cuenta nueva(Presupuesto presupuesto, String nombre) {
        return Cuenta.builder()
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(Cuenta.normalizar(nombre))
                .tipo(TipoCuenta.CORRIENTE)
                .build();
    }
}
