package com.presupuesto.comun.excepcion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ErroresNoCapturadosTest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Test
    void rutaPublicaInexistenteNoExponeStackTraceNiMensajeInterno() {
        ResponseEntity<String> respuesta =
                testRestTemplate.getForEntity("/api/v1/auth/ruta-que-no-existe", String.class);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody()).doesNotContain("Trace");
        assertThat(respuesta.getBody()).doesNotContain("No static resource");
    }
}
