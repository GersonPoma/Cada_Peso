package com.presupuesto.categoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.categoria.dto.request.ActualizarCategoriaRequest;
import com.presupuesto.categoria.dto.request.CrearCategoriaRequest;
import com.presupuesto.categoria.dto.request.MoverCategoriaRequest;
import com.presupuesto.categoria.dto.response.CategoriaResponse;
import com.presupuesto.categoria.dto.response.GrupoCategoriaConCategoriasResponse;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.service.PresupuestoService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoriaServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private GrupoCategoriaRepository grupoRepository;

    @Mock
    private PresupuestoService presupuestoService;

    private CategoriaService service;
    private GrupoCategoria x;
    private GrupoCategoria y;

    @BeforeEach
    void preparar() {
        service = new CategoriaService(categoriaRepository, grupoRepository, presupuestoService);
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenReturn(Presupuesto.builder().id(PRESUPUESTO_ID).build());
        when(categoriaRepository.saveAndFlush(any(Categoria.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        x = grupo(1L, "X", 0);
        y = grupo(2L, "Y", 1);
    }

    private GrupoCategoria grupo(long id, String nombre, int orden) {
        GrupoCategoria grupo = GrupoCategoria.builder()
                .id(id).nombre(nombre).nombreNormalizado(GrupoCategoria.normalizar(nombre))
                .orden(orden).build();
        when(grupoRepository.findByIdAndPresupuestoId(id, PRESUPUESTO_ID))
                .thenReturn(Optional.of(grupo));
        return grupo;
    }

    private Categoria categoria(long id, GrupoCategoria grupo, String nombre, int orden) {
        Categoria categoria = Categoria.builder()
                .id(id).grupo(grupo).nombre(nombre)
                .nombreNormalizado(Categoria.normalizar(nombre)).orden(orden).build();
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(id, PRESUPUESTO_ID))
                .thenReturn(Optional.of(categoria));
        return categoria;
    }

    // ---------- crear ----------

    @Test
    void crearGuardaAlFinalDelGrupoConNombreNormalizadoNotaYVisible() {
        when(categoriaRepository.countByGrupoId(1L)).thenReturn(2L);

        CategoriaResponse response = service.crear(PRESUPUESTO_ID, USUARIO_ID,
                new CrearCategoriaRequest(1L, "Mi LUZ", "Mensual"));

        ArgumentCaptor<Categoria> captor = ArgumentCaptor.forClass(Categoria.class);
        verify(categoriaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getGrupo()).isSameAs(x);
        assertThat(captor.getValue().getNombreNormalizado()).isEqualTo("mi luz");
        assertThat(response.orden()).isEqualTo(2);
        assertThat(response.nota()).isEqualTo("Mensual");
        assertThat(response.oculta()).isFalse();
        assertThat(response.grupoId()).isEqualTo(1L);
    }

    @Test
    void crearConUnNombreRepetidoEnElGrupoLanzaConflictoSinGuardar() {
        when(categoriaRepository.existsByGrupoIdAndNombreNormalizado(1L, "luz"))
                .thenReturn(true);

        ConflictoException excepcion = assertThrows(ConflictoException.class,
                () -> service.crear(PRESUPUESTO_ID, USUARIO_ID,
                        new CrearCategoriaRequest(1L, "LUZ", null)));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.CATEGORIA_YA_EXISTE);
        verify(categoriaRepository, never()).saveAndFlush(any());
    }

    @Test
    void elMismoNombreEnOtroGrupoSePermite() {
        when(categoriaRepository.existsByGrupoIdAndNombreNormalizado(1L, "otros"))
                .thenReturn(true);

        CategoriaResponse response = service.crear(PRESUPUESTO_ID, USUARIO_ID,
                new CrearCategoriaRequest(2L, "Otros", null));

        assertThat(response.grupoId()).isEqualTo(2L);
    }

    @Test
    void unaViolacionDeLaRestriccionUnicaSeTraduceAConflicto() {
        when(categoriaRepository.saveAndFlush(any(Categoria.class)))
                .thenThrow(new DataIntegrityViolationException("uk_categorias_grupo_nombre"));

        ConflictoException excepcion = assertThrows(ConflictoException.class,
                () -> service.crear(PRESUPUESTO_ID, USUARIO_ID,
                        new CrearCategoriaRequest(1L, "Luz", null)));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.CATEGORIA_YA_EXISTE);
    }

    @Test
    void crearEnUnGrupoInexistenteOAjenoLanzaNoEncontrado() {
        when(grupoRepository.findByIdAndPresupuestoId(77L, PRESUPUESTO_ID))
                .thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.crear(
                PRESUPUESTO_ID, USUARIO_ID, new CrearCategoriaRequest(77L, "Luz", null)));
        verify(categoriaRepository, never()).saveAndFlush(any());
    }

    // ---------- obtener, actualizar, ocultar, mostrar ----------

    @Test
    void obtenerDevuelveLaCategoria() {
        categoria(5L, x, "Luz", 0);

        assertThat(service.obtener(PRESUPUESTO_ID, USUARIO_ID, 5L).nombre()).isEqualTo("Luz");
    }

    @Test
    void actualizarCambiaNombreYNotaYBorraLaNotaSiViajaNula() {
        Categoria luz = categoria(5L, x, "Luz", 3);
        luz.cambiarNota("Vieja");

        CategoriaResponse response = service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 5L,
                new ActualizarCategoriaRequest("Electricidad", "Nueva"));
        assertThat(response.nombre()).isEqualTo("Electricidad");
        assertThat(response.nota()).isEqualTo("Nueva");
        assertThat(luz.getNombreNormalizado()).isEqualTo("electricidad");

        CategoriaResponse sinNota = service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 5L,
                new ActualizarCategoriaRequest("Electricidad", null));
        assertThat(sinNota.nota()).isNull();
        assertThat(sinNota.orden()).isEqualTo(3);
        assertThat(sinNota.grupoId()).isEqualTo(1L);
    }

    @Test
    void actualizarConElNombreDeOtraCategoriaDelGrupoLanzaConflicto() {
        categoria(5L, x, "Luz", 0);
        when(categoriaRepository.existsByGrupoIdAndNombreNormalizadoAndIdNot(1L, "agua", 5L))
                .thenReturn(true);

        ConflictoException excepcion = assertThrows(ConflictoException.class,
                () -> service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 5L,
                        new ActualizarCategoriaRequest("AGUA", null)));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.CATEGORIA_YA_EXISTE);
    }

    @Test
    void actualizarConSuPropioNombreCambiandoMayusculasEsValido() {
        categoria(5L, x, "luz", 0);

        assertThat(service.actualizar(PRESUPUESTO_ID, USUARIO_ID, 5L,
                new ActualizarCategoriaRequest("Luz", null)).nombre()).isEqualTo("Luz");
    }

    @Test
    void ocultarYMostrarSonIdempotentes() {
        categoria(5L, x, "Luz", 0);

        assertThat(service.ocultar(PRESUPUESTO_ID, USUARIO_ID, 5L).oculta()).isTrue();
        assertThat(service.ocultar(PRESUPUESTO_ID, USUARIO_ID, 5L).oculta()).isTrue();
        assertThat(service.mostrar(PRESUPUESTO_ID, USUARIO_ID, 5L).oculta()).isFalse();
        assertThat(service.mostrar(PRESUPUESTO_ID, USUARIO_ID, 5L).oculta()).isFalse();
    }

    // ---------- mover ----------

    @Test
    void moverDentroDelGrupoRenumeraSinHuecos() {
        Categoria a = categoria(1L, x, "A", 0);
        Categoria b = categoria(2L, x, "B", 1);
        Categoria c = categoria(3L, x, "C", 2);
        when(categoriaRepository.findByGrupoIdOrderByOrden(1L)).thenReturn(List.of(a, b, c));

        CategoriaResponse response = service.mover(
                PRESUPUESTO_ID, USUARIO_ID, 1L, new MoverCategoriaRequest(1L, 2));

        assertThat(response.orden()).isEqualTo(2);
        assertThat(b.getOrden()).isZero();
        assertThat(c.getOrden()).isEqualTo(1);
    }

    @Test
    void moverDentroDelGrupoFueraDeRangoLanzaDatosInvalidos() {
        Categoria a = categoria(1L, x, "A", 0);
        Categoria b = categoria(2L, x, "B", 1);
        when(categoriaRepository.findByGrupoIdOrderByOrden(1L)).thenReturn(List.of(a, b));

        DatosInvalidosException excepcion = assertThrows(DatosInvalidosException.class,
                () -> service.mover(PRESUPUESTO_ID, USUARIO_ID, 1L,
                        new MoverCategoriaRequest(1L, 2)));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.DATOS_INVALIDOS);
        verify(categoriaRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void moverAOtroGrupoRenumeraOrigenYDestinoSinHuecos() {
        Categoria a = categoria(1L, x, "A", 0);
        Categoria b = categoria(2L, x, "B", 1);
        Categoria c = categoria(3L, x, "C", 2);
        Categoria d = categoria(4L, y, "D", 0);
        Categoria e = categoria(5L, y, "E", 1);
        when(categoriaRepository.findByGrupoIdOrderByOrden(1L)).thenReturn(List.of(a, b, c));
        when(categoriaRepository.findByGrupoIdOrderByOrden(2L)).thenReturn(List.of(d, e));

        CategoriaResponse response = service.mover(
                PRESUPUESTO_ID, USUARIO_ID, 2L, new MoverCategoriaRequest(2L, 1));

        assertThat(response.grupoId()).isEqualTo(2L);
        assertThat(response.orden()).isEqualTo(1);
        assertThat(a.getOrden()).isZero();
        assertThat(c.getOrden()).isEqualTo(1);
        assertThat(d.getOrden()).isZero();
        assertThat(e.getOrden()).isEqualTo(2);
    }

    @Test
    void moverAlFinalDeOtroGrupoYAUnGrupoVacioEsValido() {
        Categoria a = categoria(1L, x, "A", 0);
        Categoria d = categoria(4L, y, "D", 0);
        when(categoriaRepository.findByGrupoIdOrderByOrden(1L)).thenReturn(List.of(a));
        when(categoriaRepository.findByGrupoIdOrderByOrden(2L)).thenReturn(List.of(d));

        assertThat(service.mover(PRESUPUESTO_ID, USUARIO_ID, 1L,
                new MoverCategoriaRequest(2L, 1)).orden()).isEqualTo(1);

        GrupoCategoria vacio = grupo(3L, "Vacio", 2);
        Categoria f = categoria(6L, y, "F", 1);
        when(categoriaRepository.findByGrupoIdOrderByOrden(3L)).thenReturn(List.of());
        when(categoriaRepository.findByGrupoIdOrderByOrden(2L)).thenReturn(List.of(d, f));
        CategoriaResponse enVacio = service.mover(
                PRESUPUESTO_ID, USUARIO_ID, 6L, new MoverCategoriaRequest(3L, 0));

        assertThat(enVacio.grupoId()).isEqualTo(vacio.getId());
        assertThat(enVacio.orden()).isZero();
    }

    @Test
    void moverAOtroGrupoFueraDeRangoLanzaDatosInvalidosSinModificarNada() {
        Categoria a = categoria(1L, x, "A", 0);
        Categoria d = categoria(4L, y, "D", 0);
        when(categoriaRepository.findByGrupoIdOrderByOrden(1L)).thenReturn(List.of(a));
        when(categoriaRepository.findByGrupoIdOrderByOrden(2L)).thenReturn(List.of(d));

        assertThrows(DatosInvalidosException.class, () -> service.mover(
                PRESUPUESTO_ID, USUARIO_ID, 1L, new MoverCategoriaRequest(2L, 2)));

        assertThat(a.getGrupo()).isSameAs(x);
        verify(categoriaRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void moverAUnGrupoConElMismoNombreLanzaConflictoSinModificarNada() {
        Categoria a = categoria(1L, x, "Otros", 0);
        when(categoriaRepository.findByGrupoIdOrderByOrden(1L)).thenReturn(List.of(a));
        when(categoriaRepository.findByGrupoIdOrderByOrden(2L)).thenReturn(List.of());
        when(categoriaRepository.existsByGrupoIdAndNombreNormalizado(2L, "otros"))
                .thenReturn(true);

        ConflictoException excepcion = assertThrows(ConflictoException.class,
                () -> service.mover(PRESUPUESTO_ID, USUARIO_ID, 1L,
                        new MoverCategoriaRequest(2L, 0)));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.CATEGORIA_YA_EXISTE);
        assertThat(a.getGrupo()).isSameAs(x);
        verify(categoriaRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void moverAUnGrupoInexistenteOAjenoLanzaNoEncontrado() {
        categoria(1L, x, "A", 0);
        when(grupoRepository.findByIdAndPresupuestoId(77L, PRESUPUESTO_ID))
                .thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.mover(
                PRESUPUESTO_ID, USUARIO_ID, 1L, new MoverCategoriaRequest(77L, 0)));
    }

    // ---------- árbol ----------

    @Test
    void elArbolAgrupaLasCategoriasPorGrupoEnSuOrden() {
        Categoria a = categoria(1L, x, "A", 0);
        Categoria b = categoria(2L, x, "B", 1);
        Categoria d = categoria(4L, y, "D", 0);
        GrupoCategoria z = grupo(3L, "Z", 2);
        when(grupoRepository.findByPresupuestoIdOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(x, y, z));
        when(categoriaRepository.findByGrupoPresupuestoIdOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(a, d, b));

        List<GrupoCategoriaConCategoriasResponse> arbol =
                service.arbol(PRESUPUESTO_ID, USUARIO_ID, true);

        assertThat(arbol).extracting(GrupoCategoriaConCategoriasResponse::nombre)
                .containsExactly("X", "Y", "Z");
        assertThat(arbol.get(0).categorias()).extracting(CategoriaResponse::nombre)
                .containsExactly("A", "B");
        assertThat(arbol.get(1).categorias()).extracting(CategoriaResponse::nombre)
                .containsExactly("D");
        assertThat(arbol.get(2).categorias()).isEmpty();
    }

    @Test
    void sinIncluirOcultasUsaLasConsultasQueFiltran() {
        Categoria a = categoria(1L, x, "A", 0);
        when(grupoRepository.findByPresupuestoIdAndOcultoFalseOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(x));
        when(categoriaRepository.findByGrupoPresupuestoIdAndOcultaFalseOrderByOrden(
                PRESUPUESTO_ID)).thenReturn(List.of(a));

        List<GrupoCategoriaConCategoriasResponse> arbol =
                service.arbol(PRESUPUESTO_ID, USUARIO_ID, false);

        assertThat(arbol).hasSize(1);
        assertThat(arbol.get(0).categorias()).hasSize(1);
        verify(grupoRepository, never()).findByPresupuestoIdOrderByOrden(any());
        verify(categoriaRepository, never()).findByGrupoPresupuestoIdOrderByOrden(any());
    }

    // ---------- aislamiento ----------

    @Test
    void unPresupuestoAjenoPropagaElNoEncontradoEnTodasLasOperaciones() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, 99L))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class, () -> service.crear(
                PRESUPUESTO_ID, 99L, new CrearCategoriaRequest(1L, "Luz", null)));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.arbol(PRESUPUESTO_ID, 99L, false));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtener(PRESUPUESTO_ID, 99L, 5L));
        assertThrows(RecursoNoEncontradoException.class, () -> service.actualizar(
                PRESUPUESTO_ID, 99L, 5L, new ActualizarCategoriaRequest("Luz", null)));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.ocultar(PRESUPUESTO_ID, 99L, 5L));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.mostrar(PRESUPUESTO_ID, 99L, 5L));
        assertThrows(RecursoNoEncontradoException.class, () -> service.mover(
                PRESUPUESTO_ID, 99L, 5L, new MoverCategoriaRequest(1L, 0)));
        verify(categoriaRepository, never()).saveAndFlush(any());
    }

    @Test
    void unaCategoriaInexistenteOAjenaLanzaNoEncontradoEnTodasLasOperaciones() {
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(77L, PRESUPUESTO_ID))
                .thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, 77L));
        assertThrows(RecursoNoEncontradoException.class, () -> service.actualizar(
                PRESUPUESTO_ID, USUARIO_ID, 77L, new ActualizarCategoriaRequest("Luz", null)));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.ocultar(PRESUPUESTO_ID, USUARIO_ID, 77L));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.mostrar(PRESUPUESTO_ID, USUARIO_ID, 77L));
        assertThrows(RecursoNoEncontradoException.class, () -> service.mover(
                PRESUPUESTO_ID, USUARIO_ID, 77L, new MoverCategoriaRequest(1L, 0)));
    }
}
