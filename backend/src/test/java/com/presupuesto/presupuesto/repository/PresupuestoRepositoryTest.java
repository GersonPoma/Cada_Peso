package com.presupuesto.presupuesto.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.presupuesto.entity.Presupuesto;
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
class PresupuestoRepositoryTest {

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario ana;
    private Usuario beto;

    @BeforeEach
    void crearUsuarios() {
        ana = usuarioRepository.saveAndFlush(nuevoUsuario("ana-pres@ejemplo.com"));
        beto = usuarioRepository.saveAndFlush(nuevoUsuario("beto-pres@ejemplo.com"));
    }

    @Test
    void findByIdAndUsuarioIdSoloEncuentraElPresupuestoDeSuDueno() {
        Presupuesto deAna = presupuestoRepository.saveAndFlush(nuevo(ana, "Casa"));

        assertThat(presupuestoRepository.findByIdAndUsuarioId(deAna.getId(), ana.getId()))
                .isPresent();
        assertThat(presupuestoRepository.findByIdAndUsuarioId(deAna.getId(), beto.getId()))
                .isEmpty();
        assertThat(presupuestoRepository.findByIdAndUsuarioId(Long.MAX_VALUE, ana.getId()))
                .isEmpty();
    }

    @Test
    void laListaEsDelUsuarioYEstaOrdenadaPorNombreSinDistinguirMayusculas() {
        presupuestoRepository.save(nuevo(ana, "Viajes"));
        presupuestoRepository.save(nuevo(ana, "casa"));
        presupuestoRepository.save(nuevo(ana, "Ahorros"));
        presupuestoRepository.saveAndFlush(nuevo(beto, "Otro"));

        assertThat(presupuestoRepository.findByUsuarioIdOrderByNombreNormalizado(ana.getId()))
                .extracting(Presupuesto::getNombre)
                .containsExactly("Ahorros", "casa", "Viajes");
    }

    @Test
    void existsPorNombreNormalizadoRespetaAlUsuarioYExcluyeElPropioId() {
        Presupuesto casa = presupuestoRepository.saveAndFlush(nuevo(ana, "Casa"));

        assertThat(presupuestoRepository.existsByUsuarioIdAndNombreNormalizado(
                ana.getId(), "casa")).isTrue();
        assertThat(presupuestoRepository.existsByUsuarioIdAndNombreNormalizado(
                beto.getId(), "casa")).isFalse();
        assertThat(presupuestoRepository.existsByUsuarioIdAndNombreNormalizadoAndIdNot(
                ana.getId(), "casa", casa.getId())).isFalse();
        assertThat(presupuestoRepository.existsByUsuarioIdAndNombreNormalizadoAndIdNot(
                ana.getId(), "casa", Long.MAX_VALUE)).isTrue();
    }

    @Test
    void laRestriccionUnicaRechazaElMismoNombreNormalizadoDelMismoUsuario() {
        presupuestoRepository.saveAndFlush(nuevo(ana, "Casa"));

        assertThrows(DataIntegrityViolationException.class,
                () -> presupuestoRepository.saveAndFlush(nuevo(ana, "CASA")));
    }

    @Test
    void laRestriccionUnicaPermiteElMismoNombreEnOtroUsuario() {
        presupuestoRepository.saveAndFlush(nuevo(ana, "Casa"));

        Presupuesto deBeto = presupuestoRepository.saveAndFlush(nuevo(beto, "Casa"));

        assertThat(deBeto.getId()).isNotNull();
    }

    private static Usuario nuevoUsuario(String email) {
        return Usuario.builder().email(email).contrasena("hash-de-prueba").build();
    }

    private static Presupuesto nuevo(Usuario usuario, String nombre) {
        return Presupuesto.builder()
                .usuario(usuario)
                .nombre(nombre)
                .nombreNormalizado(Presupuesto.normalizar(nombre))
                .moneda("BOB")
                .build();
    }
}
