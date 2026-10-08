package com.presupuesto.categoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.entity.TipoGrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Comprueba con Mockito que el grupo de pagos se obtiene con el presupuesto bloqueado. La
 * carrera real entre dos procesos no se prueba: queda como riesgo documentado en el design.
 */
class CategoriasPagoTarjetaBloqueoTest {

    private static final long PRESUPUESTO_ID = 20L;

    private GrupoCategoriaRepository grupoRepository;
    private PresupuestoRepository presupuestoRepository;
    private CategoriasPagoTarjeta categorias;
    private Presupuesto presupuesto;
    private GrupoCategoria pagos;

    @BeforeEach
    void preparar() {
        grupoRepository = mock(GrupoCategoriaRepository.class);
        presupuestoRepository = mock(PresupuestoRepository.class);
        categorias = new CategoriasPagoTarjeta(
                grupoRepository, mock(CategoriaRepository.class), presupuestoRepository);
        presupuesto = Presupuesto.builder().id(PRESUPUESTO_ID).build();
        pagos = GrupoCategoria.builder().id(7L).presupuesto(presupuesto)
                .tipo(TipoGrupoCategoria.PAGOS_TARJETA).build();
        when(presupuestoRepository.findByIdParaActualizar(PRESUPUESTO_ID))
                .thenReturn(Optional.of(presupuesto));
        when(grupoRepository.saveAndFlush(any(GrupoCategoria.class))).thenReturn(pagos);
    }

    @Test
    void asegurarGrupoObtieneElPresupuestoConLaConsultaConBloqueoAntesDeBuscarElGrupo() {
        when(grupoRepository.findFirstByPresupuestoIdAndTipo(
                PRESUPUESTO_ID, TipoGrupoCategoria.PAGOS_TARJETA))
                .thenReturn(Optional.empty());

        categorias.asegurarGrupo(PRESUPUESTO_ID);

        InOrder orden = inOrder(presupuestoRepository, grupoRepository);
        orden.verify(presupuestoRepository).findByIdParaActualizar(PRESUPUESTO_ID);
        orden.verify(grupoRepository).findFirstByPresupuestoIdAndTipo(
                PRESUPUESTO_ID, TipoGrupoCategoria.PAGOS_TARJETA);
        verify(presupuestoRepository, never()).findById(any());
    }

    @Test
    void dosLlamadasSeguidasDevuelvenElMismoGrupoSinCrearOtro() {
        when(grupoRepository.findFirstByPresupuestoIdAndTipo(
                PRESUPUESTO_ID, TipoGrupoCategoria.PAGOS_TARJETA))
                .thenReturn(Optional.empty(), Optional.of(pagos));

        GrupoCategoria primero = categorias.asegurarGrupo(PRESUPUESTO_ID);
        GrupoCategoria segundo = categorias.asegurarGrupo(PRESUPUESTO_ID);

        assertThat(primero).isSameAs(pagos);
        assertThat(segundo).isSameAs(pagos);
        verify(grupoRepository, times(1)).saveAndFlush(any(GrupoCategoria.class));
        verify(presupuestoRepository, times(2)).findByIdParaActualizar(PRESUPUESTO_ID);
        verify(presupuestoRepository, never()).findById(any());
    }

    @Test
    void sinPresupuestoLanzaNoEncontradoYNoCreaNada() {
        when(presupuestoRepository.findByIdParaActualizar(PRESUPUESTO_ID))
                .thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> categorias.asegurarGrupo(PRESUPUESTO_ID));

        verify(grupoRepository, never()).saveAndFlush(any(GrupoCategoria.class));
    }
}
