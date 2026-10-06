package com.presupuesto.categoria.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
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
class CategoriaRepositoryTest {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private GrupoCategoriaRepository grupoRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Presupuesto casa;
    private Presupuesto viajes;
    private GrupoCategoria vivienda;
    private GrupoCategoria comida;
    private GrupoCategoria ajeno;

    @BeforeEach
    void crearDatos() {
        Usuario ana = usuarioRepository.saveAndFlush(
                Usuario.builder().email("ana-categorias@ejemplo.com").contrasena("hash").build());
        casa = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Casa"));
        viajes = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Viajes"));
        vivienda = grupoRepository.saveAndFlush(nuevoGrupo(casa, "Vivienda", 0));
        comida = grupoRepository.saveAndFlush(nuevoGrupo(casa, "Comida", 1));
        ajeno = grupoRepository.saveAndFlush(nuevoGrupo(viajes, "Vuelos", 0));
    }

    @Test
    void findByIdAndGrupoPresupuestoIdSoloEncuentraLaCategoriaDeEsePresupuesto() {
        Categoria alquiler = categoriaRepository.saveAndFlush(nueva(vivienda, "Alquiler", 0));

        assertThat(categoriaRepository.findByIdAndGrupoPresupuestoId(
                alquiler.getId(), casa.getId())).isPresent();
        assertThat(categoriaRepository.findByIdAndGrupoPresupuestoId(
                alquiler.getId(), viajes.getId())).isEmpty();
        assertThat(categoriaRepository.findByIdAndGrupoPresupuestoId(
                Long.MAX_VALUE, casa.getId())).isEmpty();
    }

    @Test
    void laListaDeUnGrupoEstaOrdenadaPorOrdenYEsSoloDeEseGrupo() {
        categoriaRepository.save(nueva(vivienda, "Segunda", 1));
        categoriaRepository.save(nueva(vivienda, "Primera", 0));
        categoriaRepository.saveAndFlush(nueva(comida, "Otra", 0));

        assertThat(categoriaRepository.findByGrupoIdOrderByOrden(vivienda.getId()))
                .extracting(Categoria::getNombre)
                .containsExactly("Primera", "Segunda");
    }

    @Test
    void laListaDelPresupuestoEsSoloDeEsePresupuestoYFiltraLasOcultas() {
        categoriaRepository.save(nueva(vivienda, "Alquiler", 0));
        categoriaRepository.save(nueva(comida, "Mercado", 0));
        Categoria oculta = nueva(vivienda, "Antigua", 1);
        oculta.ocultar();
        categoriaRepository.save(oculta);
        categoriaRepository.saveAndFlush(nueva(ajeno, "Pasajes", 0));

        assertThat(categoriaRepository.findByGrupoPresupuestoIdOrderByOrden(casa.getId()))
                .extracting(Categoria::getNombre)
                .containsExactlyInAnyOrder("Alquiler", "Mercado", "Antigua");
        assertThat(categoriaRepository
                .findByGrupoPresupuestoIdAndOcultaFalseOrderByOrden(casa.getId()))
                .extracting(Categoria::getNombre)
                .containsExactlyInAnyOrder("Alquiler", "Mercado");
    }

    @Test
    void countCuentaSoloLasCategoriasDelGrupo() {
        categoriaRepository.save(nueva(vivienda, "Uno", 0));
        categoriaRepository.save(nueva(vivienda, "Dos", 1));
        categoriaRepository.saveAndFlush(nueva(comida, "Tres", 0));

        assertThat(categoriaRepository.countByGrupoId(vivienda.getId())).isEqualTo(2);
        assertThat(categoriaRepository.countByGrupoId(comida.getId())).isEqualTo(1);
    }

    @Test
    void existsPorNombreNormalizadoRespetaAlGrupoYExcluyeElPropioId() {
        Categoria alquiler = categoriaRepository.saveAndFlush(nueva(vivienda, "Alquiler", 0));

        assertThat(categoriaRepository.existsByGrupoIdAndNombreNormalizado(
                vivienda.getId(), "alquiler")).isTrue();
        assertThat(categoriaRepository.existsByGrupoIdAndNombreNormalizado(
                comida.getId(), "alquiler")).isFalse();
        assertThat(categoriaRepository.existsByGrupoIdAndNombreNormalizadoAndIdNot(
                vivienda.getId(), "alquiler", alquiler.getId())).isFalse();
        assertThat(categoriaRepository.existsByGrupoIdAndNombreNormalizadoAndIdNot(
                vivienda.getId(), "alquiler", Long.MAX_VALUE)).isTrue();
    }

    @Test
    void laRestriccionUnicaRechazaElMismoNombreNormalizadoDelMismoGrupo() {
        categoriaRepository.saveAndFlush(nueva(vivienda, "Alquiler", 0));

        assertThrows(DataIntegrityViolationException.class,
                () -> categoriaRepository.saveAndFlush(nueva(vivienda, "ALQUILER", 1)));
    }

    @Test
    void laRestriccionUnicaPermiteElMismoNombreEnOtroGrupo() {
        categoriaRepository.saveAndFlush(nueva(vivienda, "Otros", 0));

        Categoria enComida = categoriaRepository.saveAndFlush(nueva(comida, "Otros", 0));

        assertThat(enComida.getId()).isNotNull();
    }

    private static Presupuesto nuevoPresupuesto(Usuario usuario, String nombre) {
        return Presupuesto.builder()
                .usuario(usuario)
                .nombre(nombre)
                .nombreNormalizado(Presupuesto.normalizar(nombre))
                .moneda("BOB")
                .build();
    }

    private static GrupoCategoria nuevoGrupo(Presupuesto presupuesto, String nombre, int orden) {
        return GrupoCategoria.builder()
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(GrupoCategoria.normalizar(nombre))
                .orden(orden)
                .build();
    }

    private static Categoria nueva(GrupoCategoria grupo, String nombre, int orden) {
        return Categoria.builder()
                .grupo(grupo)
                .nombre(nombre)
                .nombreNormalizado(Categoria.normalizar(nombre))
                .orden(orden)
                .build();
    }
}
