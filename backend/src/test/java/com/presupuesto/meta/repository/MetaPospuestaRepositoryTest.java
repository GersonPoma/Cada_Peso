package com.presupuesto.meta.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.entity.MetaPospuesta;
import com.presupuesto.meta.entity.TipoMeta;
import com.presupuesto.meta.repository.MetaPospuestaRepository.PospuestaEnMes;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MetaPospuestaRepositoryTest {

    private static final LocalDate OCTUBRE = LocalDate.of(2026, 10, 1);
    private static final LocalDate NOVIEMBRE = LocalDate.of(2026, 11, 1);

    @Autowired
    private MetaPospuestaRepository repository;

    @Autowired
    private MetaRepository metaRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private GrupoCategoriaRepository grupoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private Presupuesto casa;
    private Presupuesto viajes;
    private Meta deComida;
    private Meta deAvion;

    @BeforeEach
    void crearDatos() {
        Usuario ana = usuarioRepository.saveAndFlush(Usuario.builder()
                .email("ana-pospuestas@ejemplo.com").contrasena("hash").build());
        casa = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Casa"));
        viajes = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Viajes"));
        deComida = metaRepository.save(nueva(categoria(casa, "Comida")));
        deAvion = metaRepository.saveAndFlush(nueva(categoria(viajes, "Avion")));
    }

    @Test
    void unaMetaNoSePuedePosponerDosVecesEnElMismoMes() {
        repository.saveAndFlush(pospuesta(deComida, OCTUBRE));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(pospuesta(deComida, OCTUBRE)));
    }

    @Test
    void findByMetaIdAndMesEncuentraSoloEsaCombinacion() {
        MetaPospuesta guardada = repository.saveAndFlush(pospuesta(deComida, OCTUBRE));

        assertThat(repository.findByMetaIdAndMes(deComida.getId(), OCTUBRE)).contains(guardada);
        assertThat(repository.findByMetaIdAndMes(deComida.getId(), NOVIEMBRE)).isEmpty();
    }

    @Test
    void lasMetasPospuestasDeUnMesSonSoloLasDeEsePresupuesto() {
        repository.save(pospuesta(deComida, OCTUBRE));
        repository.save(pospuesta(deComida, NOVIEMBRE));
        repository.saveAndFlush(pospuesta(deAvion, OCTUBRE));

        assertThat(repository.findMetaIdsPospuestas(casa.getId(), OCTUBRE))
                .containsExactly(deComida.getId());
        assertThat(repository.findMetaIdsPospuestas(viajes.getId(), OCTUBRE))
                .containsExactly(deAvion.getId());
    }

    @Test
    void lasPospuestasEnUnRangoTraenSoloLasDeEsePresupuestoDentroDelRango() {
        LocalDate septiembre = LocalDate.of(2026, 9, 1);
        LocalDate diciembre = LocalDate.of(2026, 12, 1);
        repository.save(pospuesta(deComida, septiembre));
        repository.save(pospuesta(deComida, OCTUBRE));
        repository.save(pospuesta(deComida, NOVIEMBRE));
        repository.save(pospuesta(deComida, diciembre));
        repository.saveAndFlush(pospuesta(deAvion, OCTUBRE));

        List<PospuestaEnMes> filas =
                repository.findPospuestasEnRango(casa.getId(), OCTUBRE, NOVIEMBRE);

        assertThat(filas).extracting(PospuestaEnMes::getMetaId)
                .containsOnly(deComida.getId());
        assertThat(filas).extracting(PospuestaEnMes::getMes)
                .containsExactlyInAnyOrder(OCTUBRE, NOVIEMBRE);
        assertThat(repository.findPospuestasEnRango(viajes.getId(), OCTUBRE, NOVIEMBRE))
                .extracting(PospuestaEnMes::getMetaId).containsExactly(deAvion.getId());
        assertThat(repository.findPospuestasEnRango(casa.getId(), diciembre.plusMonths(1),
                diciembre.plusMonths(2))).isEmpty();
    }

    @Test
    void elResultadoDeUnMesEnElRangoIgualaALasPospuestasDeEseMes() {
        repository.save(pospuesta(deComida, OCTUBRE));
        repository.saveAndFlush(pospuesta(deAvion, OCTUBRE));

        List<Long> delRango = repository.findPospuestasEnRango(casa.getId(), OCTUBRE, OCTUBRE)
                .stream().map(PospuestaEnMes::getMetaId).toList();

        assertThat(delRango).containsExactlyElementsOf(
                repository.findMetaIdsPospuestas(casa.getId(), OCTUBRE));
    }

    @Test
    void borrarPorMetaBorraTodasSusPospuestasYNoLasDeOtra() {
        repository.save(pospuesta(deComida, OCTUBRE));
        repository.save(pospuesta(deComida, NOVIEMBRE));
        repository.saveAndFlush(pospuesta(deAvion, OCTUBRE));

        repository.deleteByMetaId(deComida.getId());
        repository.flush();

        assertThat(repository.countByMetaId(deComida.getId())).isZero();
        assertThat(repository.countByMetaId(deAvion.getId())).isEqualTo(1);
    }

    private Categoria categoria(Presupuesto presupuesto, String nombre) {
        GrupoCategoria grupo = grupoRepository.save(GrupoCategoria.builder()
                .presupuesto(presupuesto)
                .nombre("Grupo " + nombre)
                .nombreNormalizado(GrupoCategoria.normalizar("Grupo " + nombre))
                .orden(0)
                .build());
        return categoriaRepository.save(Categoria.builder()
                .grupo(grupo)
                .nombre(nombre)
                .nombreNormalizado(Categoria.normalizar(nombre))
                .orden(0)
                .build());
    }

    private static Meta nueva(Categoria categoria) {
        return Meta.builder()
                .categoria(categoria)
                .tipo(TipoMeta.SALDO_OBJETIVO)
                .monto(1_000L)
                .build();
    }

    private static MetaPospuesta pospuesta(Meta meta, LocalDate mes) {
        return MetaPospuesta.builder().meta(meta).mes(mes).build();
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
