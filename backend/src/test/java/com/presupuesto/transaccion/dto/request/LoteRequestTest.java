package com.presupuesto.transaccion.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoteRequestTest {

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

    @Test
    void tieneLasTresOperaciones() {
        assertThat(OperacionLote.values())
                .containsExactly(
                        OperacionLote.CATEGORIZAR, OperacionLote.APROBAR, OperacionLote.BORRAR);
    }

    @Test
    void entreUnoYCienIdsEsValido() {
        assertThat(validator.validate(request(ids(1)))).isEmpty();
        assertThat(validator.validate(request(ids(100)))).isEmpty();
    }

    @Test
    void ceroOCientoUnoIdsSeRechaza() {
        assertThat(validator.validate(request(List.of()))).hasSize(1);
        assertThat(validator.validate(request(ids(101)))).hasSize(1);
        assertThat(validator.validate(request(null))).hasSize(1);
    }

    @Test
    void unIdNuloSeRechaza() {
        List<Long> conNulo = new ArrayList<>();
        conNulo.add(1L);
        conNulo.add(null);

        assertThat(validator.validate(request(conNulo))).hasSize(1);
    }

    @Test
    void laOperacionEsObligatoria() {
        assertThat(validator.validate(new LoteRequest(ids(1), null, null))).hasSize(1);
    }

    private static LoteRequest request(List<Long> ids) {
        return new LoteRequest(ids, OperacionLote.APROBAR, null);
    }

    private static List<Long> ids(int cantidad) {
        return LongStream.rangeClosed(1, cantidad).boxed().toList();
    }
}
