package com.presupuesto.categoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.evento.PresupuestoCreadoEvento;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CategoriasInicialesListenerTest {

    private GrupoCategoriaRepository grupoRepository;
    private CategoriaRepository categoriaRepository;
    private CategoriasInicialesListener listener;
    private Presupuesto presupuesto;

    @BeforeEach
    void preparar() {
        grupoRepository = mock(GrupoCategoriaRepository.class);
        categoriaRepository = mock(CategoriaRepository.class);
        when(grupoRepository.save(any(GrupoCategoria.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        listener = new CategoriasInicialesListener(grupoRepository, categoriaRepository);
        presupuesto = Presupuesto.builder().id(3L).nombre("Casa").build();
    }

    @Test
    void creaLosGruposEnOrdenYSinOcultar() {
        listener.alCrearseElPresupuesto(new PresupuestoCreadoEvento(presupuesto));

        ArgumentCaptor<GrupoCategoria> captor = ArgumentCaptor.forClass(GrupoCategoria.class);
        verify(grupoRepository, times(4)).save(captor.capture());
        List<GrupoCategoria> grupos = captor.getAllValues();
        assertThat(grupos).extracting(GrupoCategoria::getNombre)
                .containsExactly("Facturas", "Necesidades", "Deseos", "Ahorro");
        assertThat(grupos).extracting(GrupoCategoria::getNombreNormalizado)
                .containsExactly("facturas", "necesidades", "deseos", "ahorro");
        assertThat(grupos).extracting(GrupoCategoria::getOrden).containsExactly(0, 1, 2, 3);
        assertThat(grupos).extracting(GrupoCategoria::isOculto).containsOnly(false);
        assertThat(grupos).extracting(GrupoCategoria::getPresupuesto).containsOnly(presupuesto);
    }

    @Test
    @SuppressWarnings("unchecked")
    void creaLasCategoriasConOrdenConsecutivoPorGrupo() {
        listener.alCrearseElPresupuesto(new PresupuestoCreadoEvento(presupuesto));

        ArgumentCaptor<List<Categoria>> captor = ArgumentCaptor.forClass(List.class);
        verify(categoriaRepository).saveAll(captor.capture());
        List<Categoria> categorias = captor.getValue();
        assertThat(categorias).extracting(Categoria::getNombre).containsExactly(
                "Alquiler", "Luz", "Agua", "Internet", "Teléfono", "Comida", "Transporte",
                "Salud", "Restaurantes", "Ocio", "Ropa", "Fondo de emergencia", "Vacaciones");
        assertThat(categorias).extracting(Categoria::getOrden)
                .containsExactly(0, 1, 2, 3, 4, 0, 1, 2, 0, 1, 2, 0, 1);
        assertThat(categorias).extracting(Categoria::isOculta).containsOnly(false);
        assertThat(categorias.get(4).getNombreNormalizado()).isEqualTo("teléfono");
        assertThat(categorias.get(0).getGrupo().getNombre()).isEqualTo("Facturas");
        assertThat(categorias.get(12).getGrupo().getNombre()).isEqualTo("Ahorro");
    }

    @Test
    void unaExcepcionDelRepositorioSePropaga() {
        when(grupoRepository.save(any(GrupoCategoria.class)))
                .thenThrow(new IllegalStateException("fallo simulado"));

        assertThrows(IllegalStateException.class,
                () -> listener.alCrearseElPresupuesto(new PresupuestoCreadoEvento(presupuesto)));
    }
}
