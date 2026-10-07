package com.presupuesto.asignacion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.asignacion.entity.AsignacionMensual;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
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
class AsignacionMensualRepositoryTest {

    private static final LocalDate ENERO = LocalDate.of(2026, 1, 1);
    private static final LocalDate FEBRERO = LocalDate.of(2026, 2, 1);
    private static final LocalDate MARZO = LocalDate.of(2026, 3, 1);

    @Autowired
    private AsignacionMensualRepository repository;

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
    private Categoria comida;
    private Categoria ocio;
    private Categoria hotel;

    @BeforeEach
    void crearDatos() {
        Usuario ana = usuarioRepository.saveAndFlush(
                Usuario.builder().email("ana-asignacion@ejemplo.com").contrasena("hash").build());
        casa = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Casa"));
        viajes = presupuestoRepository.saveAndFlush(nuevoPresupuesto(ana, "Viajes"));
        comida = categoria(casa, "Comida");
        ocio = categoria(casa, "Ocio");
        hotel = categoria(viajes, "Hotel");
    }

    @Test
    void findByCategoriaIdAndMesEncuentraSoloEsaCombinacion() {
        AsignacionMensual guardada = repository.saveAndFlush(nueva(comida, ENERO, 100_000L));

        assertThat(repository.findByCategoriaIdAndMes(comida.getId(), ENERO))
                .contains(guardada);
        assertThat(repository.findByCategoriaIdAndMes(comida.getId(), FEBRERO)).isEmpty();
        assertThat(repository.findByCategoriaIdAndMes(ocio.getId(), ENERO)).isEmpty();
    }

    @Test
    void asignadosHastaSeAcotaPorPresupuestoYPorMesInclusive() {
        repository.save(nueva(comida, ENERO, 100_000L));
        repository.save(nueva(comida, FEBRERO, 50_000L));
        repository.save(nueva(ocio, FEBRERO, 20_000L));
        repository.save(nueva(comida, MARZO, 9_999L));
        repository.saveAndFlush(nueva(hotel, ENERO, 7_777L));

        List<AsignacionMensualRepository.AsignadoPorMes> filas =
                repository.asignadosHasta(casa.getId(), FEBRERO);

        assertThat(filas).hasSize(3);
        assertThat(filas).extracting(AsignacionMensualRepository.AsignadoPorMes::getAsignado)
                .containsExactlyInAnyOrder(100_000L, 50_000L, 20_000L);
        assertThat(filas).extracting(AsignacionMensualRepository.AsignadoPorMes::getMes)
                .doesNotContain(MARZO);
    }

    @Test
    void laRestriccionUnicaRechazaLaMismaCategoriaYMes() {
        repository.saveAndFlush(nueva(comida, ENERO, 1L));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(nueva(comida, ENERO, 2L)));
    }

    @Test
    void laMismaCategoriaEnOtroMesYElMismoMesEnOtraCategoriaSiSeGuardan() {
        repository.saveAndFlush(nueva(comida, ENERO, 1L));

        AsignacionMensual otroMes = repository.saveAndFlush(nueva(comida, FEBRERO, 2L));
        AsignacionMensual otraCategoria = repository.saveAndFlush(nueva(ocio, ENERO, 3L));

        assertThat(otroMes.getId()).isNotNull();
        assertThat(otraCategoria.getId()).isNotNull();
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

    private static AsignacionMensual nueva(Categoria categoria, LocalDate mes, long asignado) {
        return AsignacionMensual.builder()
                .categoria(categoria).mes(mes).asignado(asignado).build();
    }

    private static Presupuesto nuevoPresupuesto(Usuario usuario, String nombre) {
        return Presupuesto.builder()
                .usuario(usuario)
                .nombre(nombre)
                .nombreNormalizado(Presupuesto.normalizar(nombre))
                .moneda("BOB")
                .build();
    }

    @Test
    void findByCategoriaIdInAndMesTraeSoloLasCategoriasYElMesPedidos() {
        AsignacionMensual deComida = repository.save(nueva(comida, ENERO, 10_000L));
        repository.save(nueva(comida, FEBRERO, 20_000L));
        AsignacionMensual deOcio = repository.save(nueva(ocio, ENERO, 30_000L));
        repository.saveAndFlush(nueva(hotel, ENERO, 40_000L));

        assertThat(repository.findByCategoriaIdInAndMes(
                List.of(comida.getId(), ocio.getId()), ENERO))
                .containsExactlyInAnyOrder(deComida, deOcio);
        assertThat(repository.findByCategoriaIdInAndMes(List.of(ocio.getId()), FEBRERO))
                .isEmpty();
    }
}
