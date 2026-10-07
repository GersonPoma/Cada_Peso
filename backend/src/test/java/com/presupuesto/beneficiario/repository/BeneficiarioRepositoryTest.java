package com.presupuesto.beneficiario.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.beneficiario.entity.Beneficiario;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BeneficiarioRepositoryTest {

    @Autowired
    private BeneficiarioRepository beneficiarioRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Presupuesto casa;
    private Presupuesto viajes;

    @BeforeEach
    void crearPresupuestos() {
        Usuario ana = usuarioRepository.saveAndFlush(
                Usuario.builder().email("ana-beneficiario@ejemplo.com").contrasena("hash").build());
        casa = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Casa"));
        viajes = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Viajes"));
    }

    @Test
    void findByIdAndPresupuestoIdSoloEncuentraElBeneficiarioDeEsePresupuesto() {
        Beneficiario netflix = beneficiarioRepository.saveAndFlush(nuevo(casa, "Netflix"));

        assertThat(beneficiarioRepository.findByIdAndPresupuestoId(netflix.getId(), casa.getId()))
                .isPresent();
        assertThat(beneficiarioRepository
                .findByIdAndPresupuestoId(netflix.getId(), viajes.getId())).isEmpty();
        assertThat(beneficiarioRepository
                .findByIdAndPresupuestoId(Long.MAX_VALUE, casa.getId())).isEmpty();
    }

    @Test
    void laListaEsDelPresupuestoYEstaOrdenadaPorNombreNormalizado() {
        beneficiarioRepository.save(nuevo(casa, "zapateria"));
        beneficiarioRepository.save(nuevo(casa, "Banco"));
        beneficiarioRepository.save(nuevo(casa, "alquiler"));
        beneficiarioRepository.saveAndFlush(nuevo(viajes, "Otro"));

        assertThat(beneficiarioRepository.findByPresupuestoIdOrderByNombreNormalizado(casa.getId()))
                .extracting(Beneficiario::getNombre)
                .containsExactly("alquiler", "Banco", "zapateria");
    }

    @Test
    void existsYFindPorNombreNormalizadoSeLimitanAlPresupuesto() {
        Beneficiario netflix = beneficiarioRepository.saveAndFlush(nuevo(casa, "Netflix"));

        assertThat(beneficiarioRepository
                .existsByPresupuestoIdAndNombreNormalizado(casa.getId(), "netflix")).isTrue();
        assertThat(beneficiarioRepository
                .existsByPresupuestoIdAndNombreNormalizado(viajes.getId(), "netflix")).isFalse();
        assertThat(beneficiarioRepository
                .findByPresupuestoIdAndNombreNormalizado(casa.getId(), "netflix"))
                .contains(netflix);
        assertThat(beneficiarioRepository
                .findByPresupuestoIdAndNombreNormalizado(viajes.getId(), "netflix")).isEmpty();
    }

    @Test
    void existsExcluyendoAlPropioBeneficiario() {
        Beneficiario netflix = beneficiarioRepository.saveAndFlush(nuevo(casa, "Netflix"));

        assertThat(beneficiarioRepository.existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
                casa.getId(), "netflix", netflix.getId())).isFalse();
        assertThat(beneficiarioRepository.existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
                casa.getId(), "netflix", Long.MAX_VALUE)).isTrue();
    }

    @Test
    void elMismoNombreSinDistinguirMayusculasViolaLaRestriccionUnica() {
        beneficiarioRepository.saveAndFlush(nuevo(casa, "Netflix"));

        assertThrows(DataIntegrityViolationException.class,
                () -> beneficiarioRepository.saveAndFlush(nuevo(casa, "NETFLIX")));
    }

    @Test
    void elMismoNombreEsValidoEnPresupuestosDistintos() {
        beneficiarioRepository.saveAndFlush(nuevo(casa, "Netflix"));

        Beneficiario otro = beneficiarioRepository.saveAndFlush(nuevo(viajes, "Netflix"));

        assertThat(otro.getId()).isNotNull();
    }

    @Test
    void buscarPorPrefijoNoDistingueMayusculasYSoloCuentaElPrefijo() {
        beneficiarioRepository.save(nuevo(casa, "Netflix"));
        beneficiarioRepository.save(nuevo(casa, "Nestle"));
        beneficiarioRepository.save(nuevo(casa, "Banco Union"));
        beneficiarioRepository.saveAndFlush(nuevo(viajes, "Nexo"));

        assertThat(buscar(casa, "ne%", 10)).extracting(Beneficiario::getNombre)
                .containsExactly("Nestle", "Netflix");
        assertThat(buscar(casa, "union%", 10)).isEmpty();
    }

    @Test
    void buscarPorPrefijoTrataLosComodinesEscapadosComoTextoLiteral() {
        beneficiarioRepository.save(nuevo(casa, "100% Natural"));
        beneficiarioRepository.save(nuevo(casa, "100 Natural"));
        beneficiarioRepository.save(nuevo(casa, "a_b"));
        beneficiarioRepository.saveAndFlush(nuevo(casa, "axb"));

        assertThat(buscar(casa, "100!%%", 10)).extracting(Beneficiario::getNombre)
                .containsExactly("100% Natural");
        assertThat(buscar(casa, "a!_%", 10)).extracting(Beneficiario::getNombre)
                .containsExactly("a_b");
    }

    @Test
    void buscarPorPrefijoAplicaElLimiteEnOrden() {
        for (int i = 0; i < 5; i++) {
            beneficiarioRepository.save(nuevo(casa, "a" + i));
        }
        beneficiarioRepository.flush();

        assertThat(buscar(casa, "a%", 3)).extracting(Beneficiario::getNombre)
                .containsExactly("a0", "a1", "a2");
    }

    private List<Beneficiario> buscar(Presupuesto presupuesto, String patron, int limite) {
        return beneficiarioRepository.buscarPorPrefijo(
                presupuesto.getId(), patron, PageRequest.of(0, limite));
    }

    private static Presupuesto nuevoPresupuesto(Usuario usuario, String nombre) {
        return Presupuesto.builder()
                .usuario(usuario)
                .nombre(nombre)
                .nombreNormalizado(Presupuesto.normalizar(nombre))
                .moneda("BOB")
                .build();
    }

    private static Beneficiario nuevo(Presupuesto presupuesto, String nombre) {
        return Beneficiario.builder()
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(Beneficiario.normalizar(nombre))
                .build();
    }
}
