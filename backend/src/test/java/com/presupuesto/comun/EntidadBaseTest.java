package com.presupuesto.comun;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

class EntidadBaseTest {

    @Entity
    @Getter
    @Setter
    @SuperBuilder
    @NoArgsConstructor
    static class EntidadDePrueba extends EntidadBase {
        private String nombre;
    }

    @Test
    void entidadQueExtiendeEntidadBaseCompilaYConstruyeConSuperBuilder() {
        EntidadDePrueba entidad = EntidadDePrueba.builder().nombre("prueba").build();

        assertThat(entidad.getNombre()).isEqualTo("prueba");
        assertThat(entidad.getId()).isNull();
    }

    @Test
    void dosEntidadesSinIdNoSonIguales() {
        EntidadDePrueba primera = EntidadDePrueba.builder().build();
        EntidadDePrueba segunda = EntidadDePrueba.builder().build();

        assertThat(primera).isNotEqualTo(segunda);
    }

    @Test
    void entidadesConMismoIdSonIguales() {
        EntidadDePrueba primera = EntidadDePrueba.builder().id(1L).build();
        EntidadDePrueba segunda = EntidadDePrueba.builder().id(1L).build();

        assertThat(primera).isEqualTo(segunda);
        assertThat(primera.hashCode()).isEqualTo(segunda.hashCode());
    }
}
