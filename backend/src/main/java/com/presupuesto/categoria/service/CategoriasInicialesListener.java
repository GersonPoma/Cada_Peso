package com.presupuesto.categoria.service;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.evento.PresupuestoCreadoEvento;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Crea el árbol inicial de todo presupuesto nuevo. Es un {@code @EventListener} síncrono, así que
 * corre en la transacción de quien creó el presupuesto: si falla, se revierte todo junto.
 */
@Component
@RequiredArgsConstructor
class CategoriasInicialesListener {

    private final GrupoCategoriaRepository grupoRepository;
    private final CategoriaRepository categoriaRepository;

    @EventListener
    void alCrearseElPresupuesto(PresupuestoCreadoEvento evento) {
        Presupuesto presupuesto = evento.presupuesto();
        List<Categoria> categorias = new ArrayList<>();
        int ordenGrupo = 0;
        for (Map.Entry<String, List<String>> entrada : CategoriasIniciales.ARBOL.entrySet()) {
            GrupoCategoria grupo = grupoRepository.save(GrupoCategoria.builder()
                    .presupuesto(presupuesto)
                    .nombre(entrada.getKey())
                    .nombreNormalizado(GrupoCategoria.normalizar(entrada.getKey()))
                    .orden(ordenGrupo++)
                    .build());
            int ordenCategoria = 0;
            for (String nombre : entrada.getValue()) {
                categorias.add(Categoria.builder()
                        .grupo(grupo)
                        .nombre(nombre)
                        .nombreNormalizado(Categoria.normalizar(nombre))
                        .orden(ordenCategoria++)
                        .build());
            }
        }
        categoriaRepository.saveAll(categorias);
    }
}
