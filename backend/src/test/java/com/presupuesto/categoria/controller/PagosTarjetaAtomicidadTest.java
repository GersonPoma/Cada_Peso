package com.presupuesto.categoria.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.PerfilRepository;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * No es {@code @Transactional}: necesita que la transacción de la creación de la tarjeta se
 * revierta sola. Si falla el oyente que crea la categoría de pago, la cuenta no debe quedar.
 * Borra al terminar los datos que crea.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PagosTarjetaAtomicidadTest {

    private static final String EMAIL = "atomicidad-tarjeta@ejemplo.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PerfilRepository perfilRepository;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @MockitoSpyBean
    private GrupoCategoriaRepository grupoRepository;

    @AfterEach
    void borrarDatosDePrueba() {
        Mockito.reset(grupoRepository);
        usuarioRepository.findByEmail(EMAIL).ifPresent(this::borrar);
    }

    @Test
    void siFallaElOyenteQueCreaLaCategoriaDePagoNoQuedaLaCuenta() throws Exception {
        String token = registrar();
        long presupuestoId = presupuestoId(token);
        long gruposAntes = grupoRepository.count();
        doThrow(new IllegalStateException("Fallo simulado al crear el grupo de pagos"))
                .when(grupoRepository).saveAndFlush(any(GrupoCategoria.class));

        assertThatThrownBy(() -> mockMvc.perform(post(
                        "/api/v1/presupuestos/" + presupuestoId + "/cuentas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Visa\", \"tipo\": \"TARJETA_CREDITO\"}")))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(cuentaRepository.findByPresupuestoIdOrderByNombreNormalizado(presupuestoId))
                .isEmpty();
        assertThat(grupoRepository.count()).isEqualTo(gruposAntes);
    }

    private String registrar() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(datos())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("token").asString();
    }

    private long presupuestoId(String token) throws Exception {
        String cuerpo = mockMvc.perform(get("/api/v1/presupuestos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get(0).get("id").asLong();
    }

    private static Map<String, Object> datos() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("email", EMAIL);
        datos.put("contrasena", "secreta123");
        datos.put("nombre", "Ana");
        datos.put("apellido", "Rojas");
        datos.put("fechaNacimiento", "1990-05-20");
        return datos;
    }

    /** Borra del hijo al padre: categorías, grupos, cuentas, presupuestos, perfil y usuario. */
    private void borrar(Usuario usuario) {
        for (Presupuesto presupuesto :
                presupuestoRepository.findByUsuarioIdOrderByNombreNormalizado(usuario.getId())) {
            categoriaRepository.deleteAll(
                    categoriaRepository.findByGrupoPresupuestoIdOrderByOrden(presupuesto.getId()));
            grupoRepository.deleteAll(
                    grupoRepository.findByPresupuestoIdOrderByOrden(presupuesto.getId()));
            cuentaRepository.deleteAll(
                    cuentaRepository.findByPresupuestoIdOrderByNombreNormalizado(
                            presupuesto.getId()));
            presupuestoRepository.delete(presupuesto);
        }
        perfilRepository.findByUsuarioId(usuario.getId()).ifPresent(perfilRepository::delete);
        usuarioRepository.delete(usuario);
    }
}
