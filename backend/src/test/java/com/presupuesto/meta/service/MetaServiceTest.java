package com.presupuesto.meta.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.NegocioException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.meta.dto.request.GuardarMetaRequest;
import com.presupuesto.meta.dto.response.MetaResponse;
import com.presupuesto.meta.entity.FrecuenciaMeta;
import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.entity.TipoMeta;
import com.presupuesto.meta.repository.MetaPospuestaRepository;
import com.presupuesto.meta.repository.MetaRepository;
import com.presupuesto.presupuesto.service.PresupuestoService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetaServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;
    private static final long COMIDA_ID = 1L;

    @Mock
    private MetaRepository metaRepository;

    @Mock
    private MetaPospuestaRepository pospuestaRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private PresupuestoService presupuestoService;

    private MetaService service;
    private Categoria comida;

    @BeforeEach
    void preparar() {
        service = new MetaService(
                metaRepository, pospuestaRepository, categoriaRepository, presupuestoService);
        comida = Categoria.builder().id(COMIDA_ID).nombre("Comida").build();
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(COMIDA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(comida));
        when(metaRepository.findByCategoriaId(COMIDA_ID)).thenReturn(Optional.empty());
        when(metaRepository.saveAndFlush(any(Meta.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private static GuardarMetaRequest mensual(long monto) {
        return new GuardarMetaRequest(
                TipoMeta.MONTO_MENSUAL, monto, FrecuenciaMeta.MENSUAL, null, null, null, null);
    }

    private static Meta existente(Categoria categoria) {
        Meta meta = Meta.builder().categoria(categoria).build();
        meta.reemplazar(TipoMeta.SALDO_OBJETIVO, 300_000L, null, null, null, null, null);
        return meta;
    }

    // ---------- guardar ----------

    @Test
    void guardarCreaLaMetaDeUnaCategoriaSinMeta() {
        MetaResponse respuesta = service.guardar(
                PRESUPUESTO_ID, USUARIO_ID, COMIDA_ID, mensual(100_000L));

        ArgumentCaptor<Meta> guardada = ArgumentCaptor.forClass(Meta.class);
        verify(metaRepository).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue().getCategoria()).isSameAs(comida);
        assertThat(respuesta.categoriaId()).isEqualTo(COMIDA_ID);
        assertThat(respuesta.tipo()).isEqualTo(TipoMeta.MONTO_MENSUAL);
        assertThat(respuesta.monto()).isEqualTo(100_000L);
        assertThat(respuesta.frecuencia()).isEqualTo(FrecuenciaMeta.MENSUAL);
    }

    @Test
    void guardarReemplazaLaMetaExistenteSinCrearOtra() {
        Meta meta = existente(comida);
        when(metaRepository.findByCategoriaId(COMIDA_ID)).thenReturn(Optional.of(meta));

        MetaResponse respuesta = service.guardar(
                PRESUPUESTO_ID, USUARIO_ID, COMIDA_ID, mensual(100_000L));

        ArgumentCaptor<Meta> guardada = ArgumentCaptor.forClass(Meta.class);
        verify(metaRepository).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue()).isSameAs(meta);
        assertThat(meta.getTipo()).isEqualTo(TipoMeta.MONTO_MENSUAL);
        assertThat(respuesta.monto()).isEqualTo(100_000L);
        verify(pospuestaRepository, never()).deleteByMetaId(any());
    }

    @Test
    void guardarConUnaCategoriaAjenaResponde404SinGuardar() {
        NegocioException error = assertThrows(RecursoNoEncontradoException.class,
                () -> service.guardar(PRESUPUESTO_ID, USUARIO_ID, 99L, mensual(1L)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.RECURSO_NO_ENCONTRADO);
        verify(metaRepository, never()).saveAndFlush(any(Meta.class));
    }

    @Test
    void guardarValidaPrimeroElPresupuesto() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.guardar(PRESUPUESTO_ID, USUARIO_ID, COMIDA_ID, mensual(1L)));

        verify(categoriaRepository, never()).findByIdAndGrupoPresupuestoId(any(), any());
        verify(metaRepository, never()).saveAndFlush(any(Meta.class));
    }

    @Test
    void guardarTraduceLaCarreraDeCreacionAConflicto() {
        when(metaRepository.saveAndFlush(any(Meta.class)))
                .thenThrow(new DataIntegrityViolationException("duplicada"));

        NegocioException error = assertThrows(ConflictoException.class,
                () -> service.guardar(PRESUPUESTO_ID, USUARIO_ID, COMIDA_ID, mensual(1L)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.CONFLICTO);
    }

    // ---------- obtener ----------

    @Test
    void obtenerDevuelveLaMetaDeLaCategoria() {
        when(metaRepository.findByCategoriaId(COMIDA_ID))
                .thenReturn(Optional.of(existente(comida)));

        MetaResponse respuesta = service.obtener(PRESUPUESTO_ID, USUARIO_ID, COMIDA_ID);

        assertThat(respuesta.tipo()).isEqualTo(TipoMeta.SALDO_OBJETIVO);
        assertThat(respuesta.monto()).isEqualTo(300_000L);
    }

    @Test
    void obtenerSinMetaOConCategoriaAjenaResponde404() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, COMIDA_ID));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, 99L));
    }

    // ---------- borrar ----------

    @Test
    void borrarBorraAntesLasPospuestasYLuegoLaMeta() {
        Meta meta = existente(comida);
        when(metaRepository.findByCategoriaId(COMIDA_ID)).thenReturn(Optional.of(meta));

        service.borrar(PRESUPUESTO_ID, USUARIO_ID, COMIDA_ID);

        InOrder orden = inOrder(pospuestaRepository, metaRepository);
        orden.verify(pospuestaRepository).deleteByMetaId(meta.getId());
        orden.verify(pospuestaRepository).flush();
        orden.verify(metaRepository).delete(meta);
    }

    @Test
    void borrarSinMetaResponde404SinBorrarNada() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.borrar(PRESUPUESTO_ID, USUARIO_ID, COMIDA_ID));

        verify(pospuestaRepository, never()).deleteByMetaId(any());
        verify(metaRepository, never()).delete(any(Meta.class));
    }

    // ---------- listar ----------

    @Test
    void listarDevuelveLasMetasDelPresupuestoEnElOrdenDelRepositorio() {
        Categoria ocio = Categoria.builder().id(2L).nombre("Ocio").build();
        when(metaRepository.findDelPresupuestoEnOrdenDelArbol(PRESUPUESTO_ID))
                .thenReturn(List.of(existente(ocio), existente(comida)));

        List<MetaResponse> respuesta = service.listar(PRESUPUESTO_ID, USUARIO_ID);

        assertThat(respuesta).extracting(MetaResponse::categoriaId).containsExactly(2L, COMIDA_ID);
        verify(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);
    }

    @Test
    void metaMensualConFechaSeGuardaConLosCamposDelTipo() {
        GuardarMetaRequest request = new GuardarMetaRequest(TipoMeta.MONTO_MENSUAL, 10_000L,
                FrecuenciaMeta.PERSONALIZADA, null, 14, LocalDate.of(2026, 10, 2), null);

        MetaResponse respuesta = service.guardar(PRESUPUESTO_ID, USUARIO_ID, COMIDA_ID, request);

        assertThat(respuesta.intervaloDias()).isEqualTo(14);
        assertThat(respuesta.fechaInicio()).isEqualTo(LocalDate.of(2026, 10, 2));
    }
}
