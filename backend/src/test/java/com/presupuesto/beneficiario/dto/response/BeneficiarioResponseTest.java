package com.presupuesto.beneficiario.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.beneficiario.entity.Beneficiario;
import com.presupuesto.categoria.entity.Categoria;
import org.junit.jupiter.api.Test;

class BeneficiarioResponseTest {

    @Test
    void desdeCopiaIdNombreYCategoriaPredeterminada() {
        Beneficiario beneficiario = Beneficiario.builder()
                .id(7L)
                .nombre("Netflix")
                .nombreNormalizado("netflix")
                .categoriaPredeterminada(Categoria.builder().id(9L).build())
                .build();

        assertThat(BeneficiarioResponse.desde(beneficiario))
                .isEqualTo(new BeneficiarioResponse(7L, "Netflix", 9L));
    }

    @Test
    void sinCategoriaPredeterminadaElIdEsNulo() {
        Beneficiario beneficiario = Beneficiario.builder().id(7L).nombre("Netflix").build();

        assertThat(BeneficiarioResponse.desde(beneficiario).categoriaPredeterminadaId())
                .isNull();
    }
}
