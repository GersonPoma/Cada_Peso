package com.presupuesto.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.presupuesto.usuario.Perfil;
import com.presupuesto.usuario.PerfilRepository;
import com.presupuesto.usuario.UsuarioRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * No es {@code @Transactional}: necesita que la transacción del registro se revierta por sí sola,
 * así que borra al terminar cualquier dato que haya quedado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistroAtomicidadTest {

    private static final String EMAIL = "atomicidad@ejemplo.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private PerfilRepository perfilRepository;

    @AfterEach
    void borrarDatosDePrueba() {
        usuarioRepository.findByEmail(EMAIL).ifPresent(usuarioRepository::delete);
    }

    @Test
    void siFallaGuardarElPerfilNoQuedaNingunUsuarioConEseEmail() {
        when(perfilRepository.save(any(Perfil.class)))
                .thenThrow(new IllegalStateException("Fallo simulado al guardar el perfil"));
        Map<String, Object> datos = RegistroIntegracionTest.datosValidosCon("email", EMAIL);

        assertThatThrownBy(() -> mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(datos))))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(usuarioRepository.findByEmail(EMAIL)).isEmpty();
    }
}
