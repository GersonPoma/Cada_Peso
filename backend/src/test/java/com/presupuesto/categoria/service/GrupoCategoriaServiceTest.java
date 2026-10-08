package com.presupuesto.categoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.categoria.dto.request.ActualizarGrupoCategoriaRequest;
import com.presupuesto.categoria.dto.request.CrearGrupoCategoriaRequest;
import com.presupuesto.categoria.dto.request.MoverGrupoCategoriaRequest;
import com.presupuesto.categoria.dto.response.GrupoCategoriaResponse;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.entity.TipoGrupoCategoria;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
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
class GrupoCategoriaServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;

    @Mock
    private GrupoCategoriaRepository grupoRepository;

    @Mock
    private PresupuestoService presupuestoService;

    private GrupoCategoriaService service;
    private Presupuesto presupuesto;

    @BeforeEach
    void preparar() {
        service = new GrupoCategoriaService(grupoRepository, presupuestoService);
        presupuesto = Presupuesto.builder().id(PRESUPUESTO_ID).build();
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenReturn(presupuesto);
        when(grupoRepository.saveAndFlush(any(GrupoCategoria.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private GrupoCategoria grupo(long id, String nombre, int orden) {
        GrupoCategoria grupo = GrupoCategoria.builder()
                .id(id)
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(GrupoCategoria.normalizar(nombre))
                .orden(orden)
                .build();
        when(grupoRepository.findByIdAndPresupuestoId(id, PRESUPUESTO_ID))
                .thenReturn(Optional.of(grupo));
        return grupo;
    }

    @Test
    void crearGuardaElGrupoAlFinalConNombreNormalizadoYVisible() {
        when(grupoRepository.countByPresupuestoId(PRESUPUESTO_ID)).thenReturn(3L);

        GrupoCategoriaResponse response = service.crear(
                PRESUPUESTO_ID, USUARIO_ID, new CrearGrupoCategoriaRequest("Mi GRUPO"));

        ArgumentCaptor<GrupoCategoria> captor = ArgumentCaptor.forClass(GrupoCategoria.class);
        verify(grupoRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPresupuesto()).isSameAs(presupuesto);
        assertThat(captor.getValue().getNombreNormalizado()).isEqualTo("mi grupo");
        assertThat(response.orden()).isEqualTo(3);
        assertThat(response.oculto()).isFalse();
    }

    @Test
    void crearConUnNombreRepetidoLanzaConflictoSinGuardar() {
        when(grupoRepository.existsByPresupuestoIdAndNombreNormalizado(
                PRESUPUESTO_ID, "vivienda")).thenReturn(true);

        ConflictoException excepcion = assertThrows(ConflictoException.class,
                () -> service.crear(PRESUPUESTO_ID, USUARIO_ID,
                        new CrearGrupoCategoriaRequest("VIVIENDA")));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.GRUPO_CATEGORIA_YA_EXISTE);
        verify(grupoRepository, never()).saveAndFlush(any());
    }

    @Test
    void unaViolacionDeLaRestriccionUnicaSeTraduceAConflicto() {
        when(grupoRepository.saveAndFlush(any(GrupoCategoria.class)))
                .thenThrow(new DataIntegrityViolationException("uk_grupos_categoria"));

        ConflictoException excepcion = assertThrows(ConflictoException.class,
                () -> service.crear(PRESUPUESTO_ID, USUARIO_ID,
                        new CrearGrupoCategoriaRequest("Vivienda")));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.GRUPO_CATEGORIA_YA_EXISTE);
    }

    @Test
    void renombrarCambiaElNombreYSuFormaNormalizada() {
        GrupoCategoria grupo = grupo(1L, "Hogar", 0);

        GrupoCategoriaResponse response = service.renombrar(PRESUPUESTO_ID, USUARIO_ID, 1L,
                new ActualizarGrupoCategoriaRequest("Vida Diaria"));

        assertThat(response.nombre()).isEqualTo("Vida Diaria");
        assertThat(grupo.getNombreNormalizado()).isEqualTo("vida diaria");
    }

    @Test
    void renombrarConElNombreDeOtroGrupoLanzaConflicto() {
        grupo(1L, "Hogar", 0);
        when(grupoRepository.existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
                PRESUPUESTO_ID, "comida", 1L)).thenReturn(true);

        ConflictoException excepcion = assertThrows(ConflictoException.class,
                () -> service.renombrar(PRESUPUESTO_ID, USUARIO_ID, 1L,
                        new ActualizarGrupoCategoriaRequest("COMIDA")));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.GRUPO_CATEGORIA_YA_EXISTE);
    }

    @Test
    void renombrarConSuPropioNombreCambiandoMayusculasEsValido() {
        grupo(1L, "vivienda", 0);

        GrupoCategoriaResponse response = service.renombrar(PRESUPUESTO_ID, USUARIO_ID, 1L,
                new ActualizarGrupoCategoriaRequest("Vivienda"));

        assertThat(response.nombre()).isEqualTo("Vivienda");
    }

    @Test
    void ocultarYMostrarSonIdempotentes() {
        grupo(1L, "Hogar", 0);

        assertThat(service.ocultar(PRESUPUESTO_ID, USUARIO_ID, 1L).oculto()).isTrue();
        assertThat(service.ocultar(PRESUPUESTO_ID, USUARIO_ID, 1L).oculto()).isTrue();
        assertThat(service.mostrar(PRESUPUESTO_ID, USUARIO_ID, 1L).oculto()).isFalse();
        assertThat(service.mostrar(PRESUPUESTO_ID, USUARIO_ID, 1L).oculto()).isFalse();
    }

    @Test
    void moverHaciaAdelanteRenumeraSinHuecos() {
        GrupoCategoria a = grupo(1L, "A", 0);
        GrupoCategoria b = grupo(2L, "B", 1);
        GrupoCategoria c = grupo(3L, "C", 2);
        when(grupoRepository.findByPresupuestoIdOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(a, b, c));

        GrupoCategoriaResponse response = service.mover(
                PRESUPUESTO_ID, USUARIO_ID, 1L, new MoverGrupoCategoriaRequest(2));

        assertThat(response.orden()).isEqualTo(2);
        assertThat(b.getOrden()).isZero();
        assertThat(c.getOrden()).isEqualTo(1);
    }

    @Test
    void moverHaciaAtrasRenumeraSinHuecos() {
        GrupoCategoria a = grupo(1L, "A", 0);
        GrupoCategoria b = grupo(2L, "B", 1);
        GrupoCategoria c = grupo(3L, "C", 2);
        when(grupoRepository.findByPresupuestoIdOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(a, b, c));

        service.mover(PRESUPUESTO_ID, USUARIO_ID, 3L, new MoverGrupoCategoriaRequest(0));

        assertThat(c.getOrden()).isZero();
        assertThat(a.getOrden()).isEqualTo(1);
        assertThat(b.getOrden()).isEqualTo(2);
    }

    @Test
    void moverALaMismaPosicionNoCambiaNada() {
        GrupoCategoria a = grupo(1L, "A", 0);
        GrupoCategoria b = grupo(2L, "B", 1);
        when(grupoRepository.findByPresupuestoIdOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(a, b));

        service.mover(PRESUPUESTO_ID, USUARIO_ID, 2L, new MoverGrupoCategoriaRequest(1));

        assertThat(a.getOrden()).isZero();
        assertThat(b.getOrden()).isEqualTo(1);
    }

    @Test
    void moverIncluyeLosGruposOcultosEnLaRenumeracion() {
        GrupoCategoria a = grupo(1L, "A", 0);
        GrupoCategoria b = grupo(2L, "B", 1);
        b.ocultar();
        GrupoCategoria c = grupo(3L, "C", 2);
        when(grupoRepository.findByPresupuestoIdOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(a, b, c));

        service.mover(PRESUPUESTO_ID, USUARIO_ID, 3L, new MoverGrupoCategoriaRequest(0));

        assertThat(c.getOrden()).isZero();
        assertThat(a.getOrden()).isEqualTo(1);
        assertThat(b.getOrden()).isEqualTo(2);
    }

    @Test
    void moverFueraDeRangoLanzaDatosInvalidosSinModificarNada() {
        GrupoCategoria a = grupo(1L, "A", 0);
        GrupoCategoria b = grupo(2L, "B", 1);
        when(grupoRepository.findByPresupuestoIdOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(a, b));

        DatosInvalidosException excepcion = assertThrows(DatosInvalidosException.class,
                () -> service.mover(PRESUPUESTO_ID, USUARIO_ID, 1L,
                        new MoverGrupoCategoriaRequest(2)));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.DATOS_INVALIDOS);
        assertThat(a.getOrden()).isZero();
        verify(grupoRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void unPresupuestoAjenoPropagaElNoEncontradoEnTodasLasOperaciones() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, 99L))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class, () -> service.crear(
                PRESUPUESTO_ID, 99L, new CrearGrupoCategoriaRequest("Vivienda")));
        assertThrows(RecursoNoEncontradoException.class, () -> service.renombrar(
                PRESUPUESTO_ID, 99L, 1L, new ActualizarGrupoCategoriaRequest("Hogar")));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.ocultar(PRESUPUESTO_ID, 99L, 1L));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.mostrar(PRESUPUESTO_ID, 99L, 1L));
        assertThrows(RecursoNoEncontradoException.class, () -> service.mover(
                PRESUPUESTO_ID, 99L, 1L, new MoverGrupoCategoriaRequest(0)));
        verify(grupoRepository, never()).saveAndFlush(any());
    }

    @Test
    void unGrupoInexistenteOAjenoLanzaNoEncontradoEnTodasLasOperaciones() {
        when(grupoRepository.findByIdAndPresupuestoId(77L, PRESUPUESTO_ID))
                .thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.renombrar(
                PRESUPUESTO_ID, USUARIO_ID, 77L, new ActualizarGrupoCategoriaRequest("Hogar")));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.ocultar(PRESUPUESTO_ID, USUARIO_ID, 77L));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.mostrar(PRESUPUESTO_ID, USUARIO_ID, 77L));
        assertThrows(RecursoNoEncontradoException.class, () -> service.mover(
                PRESUPUESTO_ID, USUARIO_ID, 77L, new MoverGrupoCategoriaRequest(0)));
    }

    // ---------- grupo de pagos de tarjetas ----------

    @Test
    void elGrupoDePagosNoSeRenombraOcultaMuestraNiMueveYNoSeGuardaNada() {
        GrupoCategoria pagos = GrupoCategoria.builder()
                .id(9L).presupuesto(presupuesto).nombre("Pagos").nombreNormalizado("pagos")
                .orden(0).tipo(TipoGrupoCategoria.PAGOS_TARJETA).build();
        when(grupoRepository.findByIdAndPresupuestoId(9L, PRESUPUESTO_ID))
                .thenReturn(Optional.of(pagos));

        assertThrows(ReglaNegocioException.class, () -> service.renombrar(
                PRESUPUESTO_ID, USUARIO_ID, 9L, new ActualizarGrupoCategoriaRequest("Otro")));
        assertThrows(ReglaNegocioException.class,
                () -> service.ocultar(PRESUPUESTO_ID, USUARIO_ID, 9L));
        assertThrows(ReglaNegocioException.class,
                () -> service.mostrar(PRESUPUESTO_ID, USUARIO_ID, 9L));
        assertThrows(ReglaNegocioException.class, () -> service.mover(
                PRESUPUESTO_ID, USUARIO_ID, 9L, new MoverGrupoCategoriaRequest(0)));

        assertThat(pagos.getNombre()).isEqualTo("Pagos");
        assertThat(pagos.isOculto()).isFalse();
        verify(grupoRepository, never()).saveAndFlush(any());
        verify(grupoRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void moverUnGrupoNormalSigueFuncionandoConElGrupoDePagosEnLaLista() {
        GrupoCategoria a = grupo(1L, "A", 0);
        GrupoCategoria pagos = GrupoCategoria.builder()
                .id(9L).presupuesto(presupuesto).nombre("Pagos").nombreNormalizado("pagos")
                .orden(1).tipo(TipoGrupoCategoria.PAGOS_TARJETA).build();
        when(grupoRepository.findByPresupuestoIdOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(a, pagos));

        service.mover(PRESUPUESTO_ID, USUARIO_ID, 1L, new MoverGrupoCategoriaRequest(1));

        assertThat(a.getOrden()).isEqualTo(1);
        assertThat(pagos.getOrden()).isZero();
    }
}
