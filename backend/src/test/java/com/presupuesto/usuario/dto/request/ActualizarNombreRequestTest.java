package com.presupuesto.usuario.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.auth.dto.request.RegistroRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ActualizarNombreRequestTest {

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
    void elNombreSeRecortaConservandoMayusculasYEspaciosInternos() {
        assertThat(new ActualizarNombreRequest("  María José  ").nombre()).isEqualTo("María José");
    }

    @Test
    void recortaIgualQueElRegistro() {
        for (String nombre : new String[] {"  María José ", "\tAna\n", "   ", "", "Ana"}) {
            RegistroRequest registro = new RegistroRequest(
                    "ana@ejemplo.com", "secreta123", nombre, "Rojas",
                    LocalDate.parse("1990-05-20"), null, null);

            assertThat(new ActualizarNombreRequest(nombre).nombre()).isEqualTo(registro.nombre());
        }
    }

    @Test
    void unNombreNuloSeDejaNuloYSeRechazaSinLanzarExcepcion() {
        ActualizarNombreRequest request = new ActualizarNombreRequest(null);

        assertThat(request.nombre()).isNull();
        assertThat(esValido(request)).isFalse();
    }

    @Test
    void rechazaUnNombreVacioOSoloDeEspacios() {
        assertThat(esValido(new ActualizarNombreRequest(""))).isFalse();
        assertThat(esValido(new ActualizarNombreRequest("    "))).isFalse();
    }

    @Test
    void rechazaUnNombreDeUnCaracterTrasRecortar() {
        assertThat(esValido(new ActualizarNombreRequest(" A "))).isFalse();
    }

    @Test
    void aceptaUnNombreDeDosCaracteres() {
        assertThat(esValido(new ActualizarNombreRequest("Al"))).isTrue();
    }

    @Test
    void aceptaUnNombreDe100CaracteresRodeadoDeEspacios() {
        ActualizarNombreRequest request = new ActualizarNombreRequest("  " + "a".repeat(100) + " ");

        assertThat(request.nombre()).hasSize(100);
        assertThat(esValido(request)).isTrue();
    }

    @Test
    void rechazaUnNombreDe101Caracteres() {
        assertThat(esValido(new ActualizarNombreRequest("a".repeat(101)))).isFalse();
    }

    private static boolean esValido(ActualizarNombreRequest request) {
        return validator.validate(request).isEmpty();
    }
}
