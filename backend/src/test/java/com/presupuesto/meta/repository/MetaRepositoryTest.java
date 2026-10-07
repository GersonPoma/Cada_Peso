package com.presupuesto.meta.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.entity.TipoMeta;
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
class MetaRepositoryTest {

    @Autowired
    private MetaRepository repository;

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
    private GrupoCategoria primero;
    private GrupoCategoria segundo;

    @BeforeEach
    void crearDatos() {
        Usuario ana = usuarioRepository.saveAndFlush(
                Usuario.builder().email("ana-metas@ejemplo.com").contrasena("hash").build());
        casa = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Casa"));
        viajes = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Viajes"));
        primero = grupo(casa, "Primero", 0);
        segundo = grupo(casa, "Segundo", 1);
    }

    @Test
    void findByCategoriaIdEncuentraLaMetaDeEsaCategoria() {
        Categoria comida = categoria(primero, "Comida", 0);
        Meta guardada = repository.saveAndFlush(nueva(comida, 100_000L));

        assertThat(repository.findByCategoriaId(comida.getId())).contains(guardada);
        assertThat(repository.findByCategoriaId(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void unaSegundaMetaDeLaMismaCategoriaChocaConLaRestriccionUnica() {
        Categoria comida = categoria(primero, "Comida", 0);
        repository.saveAndFlush(nueva(comida, 100_000L));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(nueva(comida, 200_000L)));
    }

    @Test
    void laListaDelPresupuestoVaEnOrdenDelArbolIncluyeOcultasYNoMezclaPresupuestos() {
        Categoria deSegundo = categoria(segundo, "Ahorro", 0);
        Categoria oculta = categoria(primero, "Vieja", 1);
        oculta.ocultar();
        categoriaRepository.save(oculta);
        Categoria deAlquiler = categoria(primero, "Alquiler", 0);
        Categoria deOtroPresupuesto = categoria(grupo(viajes, "Vuelos", 0), "Avion", 0);
        repository.save(nueva(deSegundo, 1L));
        repository.save(nueva(oculta, 2L));
        repository.save(nueva(deAlquiler, 3L));
        repository.saveAndFlush(nueva(deOtroPresupuesto, 4L));

        assertThat(repository.findDelPresupuestoEnOrdenDelArbol(casa.getId()))
                .extracting(meta -> meta.getCategoria().getNombre())
                .containsExactly("Alquiler", "Vieja", "Ahorro");
    }

    private GrupoCategoria grupo(Presupuesto presupuesto, String nombre, int orden) {
        return grupoRepository.save(GrupoCategoria.builder()
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(GrupoCategoria.normalizar(nombre))
                .orden(orden)
                .build());
    }

    private Categoria categoria(GrupoCategoria grupo, String nombre, int orden) {
        return categoriaRepository.save(Categoria.builder()
                .grupo(grupo)
                .nombre(nombre)
                .nombreNormalizado(Categoria.normalizar(nombre))
                .orden(orden)
                .build());
    }

    private static Meta nueva(Categoria categoria, long monto) {
        return Meta.builder()
                .categoria(categoria)
                .tipo(TipoMeta.SALDO_OBJETIVO)
                .monto(monto)
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
