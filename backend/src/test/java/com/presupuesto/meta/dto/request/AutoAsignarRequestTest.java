package com.presupuesto.meta.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AutoAsignarRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void crearValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void cerrarValidator() {
        validatorFactory.close();
    }

    private static Set<String> campos(AutoAsignarRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void simularAusenteVaFalso() {
        AutoAsignarRequest request = new AutoAsignarRequest(
                EstrategiaAutoAsignar.FALTANTE_META, null, null);

        assertThat(request.simular()).isFalse();
        assertThat(request.categoriaIds()).isNull();
        assertThat(campos(request)).isEmpty();
    }

    @Test
    void simularExplicitoSeConserva() {
        assertThat(new AutoAsignarRequest(
                EstrategiaAutoAsignar.FALTANTE_META, null, true).simular()).isTrue();
    }

    @Test
    void laEstrategiaEsObligatoria() {
        assertThat(campos(new AutoAsignarRequest(null, null, false))).containsExactly("estrategia");
    }

    @Test
    void categoriaIdsVaciaEsInvalida() {
        assertThat(campos(new AutoAsignarRequest(
                EstrategiaAutoAsignar.PROMEDIO_GASTADO, List.of(), false)))
                .containsExactly("categoriaIds");
    }

    @Test
    void categoriaIdsConElementosEsValidaYNoAdmiteNulos() {
        assertThat(campos(new AutoAsignarRequest(
                EstrategiaAutoAsignar.PROMEDIO_GASTADO, List.of(1L, 2L), false))).isEmpty();
        List<Long> conNulo = new ArrayList<>();
        conNulo.add(null);
        assertThat(campos(new AutoAsignarRequest(
                EstrategiaAutoAsignar.PROMEDIO_GASTADO, conNulo, false))).hasSize(1);
    }

    @Test
    void lasCincoEstrategiasExisten() {
        assertThat(EstrategiaAutoAsignar.values()).extracting(Enum::name).containsExactly(
                "FALTANTE_META", "ASIGNADO_MES_PASADO", "GASTADO_MES_PASADO",
                "PROMEDIO_ASIGNADO", "PROMEDIO_GASTADO");
    }
}
