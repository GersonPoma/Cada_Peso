package com.presupuesto.categoria.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.entity.TipoGrupoCategoria;
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
class GrupoCategoriaRepositoryTest {

    @Autowired
    private GrupoCategoriaRepository grupoRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Presupuesto casa;
    private Presupuesto viajes;

    @BeforeEach
    void crearPresupuestos() {
        Usuario ana = usuarioRepository.saveAndFlush(
                Usuario.builder().email("ana-grupos@ejemplo.com").contrasena("hash").build());
        casa = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Casa"));
        viajes = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Viajes"));
    }

    @Test
    void findByIdAndPresupuestoIdSoloEncuentraElGrupoDeEsePresupuesto() {
        GrupoCategoria vivienda = grupoRepository.saveAndFlush(nuevo(casa, "Vivienda", 0));

        assertThat(grupoRepository.findByIdAndPresupuestoId(vivienda.getId(), casa.getId()))
                .isPresent();
        assertThat(grupoRepository.findByIdAndPresupuestoId(vivienda.getId(), viajes.getId()))
                .isEmpty();
        assertThat(grupoRepository.findByIdAndPresupuestoId(Long.MAX_VALUE, casa.getId()))
                .isEmpty();
    }

    @Test
    void laListaEsDelPresupuestoYEstaOrdenadaPorOrden() {
        grupoRepository.save(nuevo(casa, "Tercero", 2));
        grupoRepository.save(nuevo(casa, "Primero", 0));
        grupoRepository.save(nuevo(casa, "Segundo", 1));
        grupoRepository.saveAndFlush(nuevo(viajes, "Ajeno", 0));

        assertThat(grupoRepository.findByPresupuestoIdOrderByOrden(casa.getId()))
                .extracting(GrupoCategoria::getNombre)
                .containsExactly("Primero", "Segundo", "Tercero");
    }

    @Test
    void laListaDeVisiblesOmiteLosOcultos() {
        grupoRepository.save(nuevo(casa, "Visible", 0));
        GrupoCategoria oculto = nuevo(casa, "Oculto", 1);
        oculto.ocultar();
        grupoRepository.saveAndFlush(oculto);

        assertThat(grupoRepository.findByPresupuestoIdAndOcultoFalseOrderByOrden(casa.getId()))
                .extracting(GrupoCategoria::getNombre)
                .containsExactly("Visible");
        assertThat(grupoRepository.findByPresupuestoIdOrderByOrden(casa.getId()))
                .extracting(GrupoCategoria::getNombre)
                .containsExactly("Visible", "Oculto");
    }

    @Test
    void countCuentaSoloLosGruposDelPresupuesto() {
        grupoRepository.save(nuevo(casa, "Uno", 0));
        grupoRepository.save(nuevo(casa, "Dos", 1));
        grupoRepository.saveAndFlush(nuevo(viajes, "Otro", 0));

        assertThat(grupoRepository.countByPresupuestoId(casa.getId())).isEqualTo(2);
        assertThat(grupoRepository.countByPresupuestoId(viajes.getId())).isEqualTo(1);
    }

    @Test
    void existsPorNombreNormalizadoRespetaAlPresupuestoYExcluyeElPropioId() {
        GrupoCategoria vivienda = grupoRepository.saveAndFlush(nuevo(casa, "Vivienda", 0));

        assertThat(grupoRepository.existsByPresupuestoIdAndNombreNormalizado(
                casa.getId(), "vivienda")).isTrue();
        assertThat(grupoRepository.existsByPresupuestoIdAndNombreNormalizado(
                viajes.getId(), "vivienda")).isFalse();
        assertThat(grupoRepository.existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
                casa.getId(), "vivienda", vivienda.getId())).isFalse();
        assertThat(grupoRepository.existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
                casa.getId(), "vivienda", Long.MAX_VALUE)).isTrue();
    }

    @Test
    void laRestriccionUnicaRechazaElMismoNombreNormalizadoDelMismoPresupuesto() {
        grupoRepository.saveAndFlush(nuevo(casa, "Vivienda", 0));

        assertThrows(DataIntegrityViolationException.class,
                () -> grupoRepository.saveAndFlush(nuevo(casa, "VIVIENDA", 1)));
    }

    @Test
    void laRestriccionUnicaPermiteElMismoNombreEnOtroPresupuesto() {
        grupoRepository.saveAndFlush(nuevo(casa, "Vivienda", 0));

        GrupoCategoria enViajes = grupoRepository.saveAndFlush(nuevo(viajes, "Vivienda", 0));

        assertThat(enViajes.getId()).isNotNull();
    }

    @Test
    void elGrupoDePagosSeLocalizaPorTipoYNoPorNombre() {
        grupoRepository.saveAndFlush(nuevo(casa, "Pagos de tarjetas de crédito", 0));
        GrupoCategoria pagos = grupoRepository.saveAndFlush(GrupoCategoria.builder()
                .presupuesto(casa)
                .nombre("Pagos de tarjetas de crédito (2)")
                .nombreNormalizado(GrupoCategoria.normalizar("Pagos de tarjetas de crédito (2)"))
                .orden(1)
                .tipo(TipoGrupoCategoria.PAGOS_TARJETA)
                .build());

        assertThat(grupoRepository.findFirstByPresupuestoIdAndTipo(
                casa.getId(), TipoGrupoCategoria.PAGOS_TARJETA))
                .get().extracting(GrupoCategoria::getId).isEqualTo(pagos.getId());
        assertThat(grupoRepository.findFirstByPresupuestoIdAndTipo(
                viajes.getId(), TipoGrupoCategoria.PAGOS_TARJETA)).isEmpty();
    }

    @Test
    void unGrupoNuevoEsNormalPorDefecto() {
        GrupoCategoria grupo = grupoRepository.saveAndFlush(nuevo(casa, "Vivienda", 0));

        assertThat(grupo.getTipo()).isEqualTo(TipoGrupoCategoria.NORMAL);
        assertThat(grupoRepository.findFirstByPresupuestoIdAndTipo(
                casa.getId(), TipoGrupoCategoria.NORMAL)).isPresent();
    }

    private static Presupuesto nuevoPresupuesto(Usuario usuario, String nombre) {
        return Presupuesto.builder()
                .usuario(usuario)
                .nombre(nombre)
                .nombreNormalizado(Presupuesto.normalizar(nombre))
                .moneda("BOB")
                .build();
    }

    private static GrupoCategoria nuevo(Presupuesto presupuesto, String nombre, int orden) {
        return GrupoCategoria.builder()
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(GrupoCategoria.normalizar(nombre))
                .orden(orden)
                .build();
    }
}
