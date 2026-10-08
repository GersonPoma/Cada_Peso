package com.presupuesto.transaccionprogramada.controller;

import static com.presupuesto.transaccionprogramada.controller.ClienteProgramadas.cuerpo;
import static com.presupuesto.transaccionprogramada.controller.ClienteProgramadas.edicion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.comun.config.RelojDePrueba;
import com.presupuesto.comun.config.RelojDePruebaConfig;
import com.presupuesto.transaccionprogramada.controller.ClienteProgramadas.Sesion;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Una persona o un presupuesto nunca ven ni generan las plantillas de otro. Cada prueba crea sus
 * propios datos y cuenta solo dentro de ellos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RelojDePruebaConfig.class)
@Transactional
class TransaccionProgramadaAislamientoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RelojDePrueba reloj;

    private ClienteProgramadas cliente;

    @BeforeEach
    void preparar() {
        cliente = new ClienteProgramadas(mockMvc, objectMapper);
        reloj.fijar(Instant.parse("2026-10-10T15:00:00Z"));
    }

    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    @Test
    void unPresupuestoAjenoDa404EnTodasLasOperaciones() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        Sesion beto = cliente.registrar("beto@ejemplo.com");
        long deAna = ana.presupuestoId();
        long cuentaDeBeto = cliente.crearCuenta(beto, beto.presupuestoId(), "Banco");
        long plantillaDeBeto = cliente.crearYObtenerId(
                beto, beto.presupuestoId(), cuerpo(cuentaDeBeto, "2026-10-01", "DIARIA", -1));

        cliente.listar(ana, beto.presupuestoId()).andExpect(status().isNotFound());
        cliente.crear(ana, beto.presupuestoId(),
                cuerpo(cuentaDeBeto, "2026-10-15", "MENSUAL", -1))
                .andExpect(status().isNotFound());
        cliente.obtener(ana, beto.presupuestoId(), plantillaDeBeto)
                .andExpect(status().isNotFound());
        cliente.editar(ana, beto.presupuestoId(), plantillaDeBeto, edicion("MENSUAL", -2))
                .andExpect(status().isNotFound());
        cliente.pausar(ana, beto.presupuestoId(), plantillaDeBeto)
                .andExpect(status().isNotFound());
        cliente.reanudar(ana, beto.presupuestoId(), plantillaDeBeto)
                .andExpect(status().isNotFound());
        cliente.borrar(ana, beto.presupuestoId(), plantillaDeBeto)
                .andExpect(status().isNotFound());
        // POST /generar valida primero el presupuesto de la URL y no genera nada ajeno.
        cliente.generar(ana, beto.presupuestoId()).andExpect(status().isNotFound());
        cliente.obtener(beto, beto.presupuestoId(), plantillaDeBeto)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proximaFecha").value("2026-10-01"));
        assertThat(cliente.transacciones(beto, beto.presupuestoId()).get("totalElementos").asInt())
                .isZero();
        cliente.listar(ana, deAna).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unaPlantillaDeOtroPresupuestoDeLaMismaPersonaDa404() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long casa = ana.presupuestoId();
        long viajes = cliente.crearPresupuesto(ana, "Viajes");
        long cuentaViajes = cliente.crearCuenta(ana, viajes, "Efectivo");
        long plantilla = cliente.crearYObtenerId(
                ana, viajes, cuerpo(cuentaViajes, "2026-10-15", "MENSUAL", -1));

        cliente.obtener(ana, casa, plantilla).andExpect(status().isNotFound());
        cliente.editar(ana, casa, plantilla, edicion("MENSUAL", -2))
                .andExpect(status().isNotFound());
        cliente.pausar(ana, casa, plantilla).andExpect(status().isNotFound());
        cliente.reanudar(ana, casa, plantilla).andExpect(status().isNotFound());
        cliente.borrar(ana, casa, plantilla).andExpect(status().isNotFound());
        cliente.listar(ana, casa).andExpect(jsonPath("$").isEmpty());
        cliente.obtener(ana, viajes, plantilla).andExpect(status().isOk());
    }

    @Test
    void laCuentaDeOtroPresupuestoDeLaMismaPersonaDa404() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long casa = ana.presupuestoId();
        long viajes = cliente.crearPresupuesto(ana, "Viajes");
        long cuentaViajes = cliente.crearCuenta(ana, viajes, "Efectivo");

        cliente.crear(ana, casa, cuerpo(cuentaViajes, "2026-10-15", "MENSUAL", -1))
                .andExpect(status().isNotFound());
    }

    @Test
    void generarConDosPresupuestosConPlantillasVencidasSoloGeneraEnElDeLaUrl() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        long casa = ana.presupuestoId();
        long viajes = cliente.crearPresupuesto(ana, "Viajes");
        long cuentaCasa = cliente.crearCuenta(ana, casa, "Banco");
        long cuentaViajes = cliente.crearCuenta(ana, viajes, "Efectivo");
        long deCasa = cliente.crearYObtenerId(
                ana, casa, cuerpo(cuentaCasa, "2026-10-08", "DIARIA", -1));
        long deViajes = cliente.crearYObtenerId(
                ana, viajes, cuerpo(cuentaViajes, "2026-10-08", "DIARIA", -1));

        cliente.generar(ana, casa).andExpect(status().isOk())
                .andExpect(jsonPath("$.generadas").value(3))
                .andExpect(jsonPath("$.plantillasConError").value(0));

        assertThat(cliente.transacciones(ana, casa).get("totalElementos").asInt()).isEqualTo(3);
        assertThat(cliente.transacciones(ana, viajes).get("totalElementos").asInt()).isZero();
        cliente.obtener(ana, casa, deCasa)
                .andExpect(jsonPath("$.proximaFecha").value("2026-10-11"));
        cliente.obtener(ana, viajes, deViajes)
                .andExpect(jsonPath("$.proximaFecha").value("2026-10-08"));

        cliente.generar(ana, viajes).andExpect(jsonPath("$.generadas").value(3));
        assertThat(cliente.transacciones(ana, viajes).get("totalElementos").asInt()).isEqualTo(3);
    }

    @Test
    void generarDeUnaPersonaNoTocaLasPlantillasDeOtra() throws Exception {
        Sesion ana = cliente.registrar("ana@ejemplo.com");
        Sesion beto = cliente.registrar("beto@ejemplo.com");
        long cuentaAna = cliente.crearCuenta(ana, ana.presupuestoId(), "Banco");
        long cuentaBeto = cliente.crearCuenta(beto, beto.presupuestoId(), "Banco");
        cliente.crearYObtenerId(ana, ana.presupuestoId(),
                cuerpo(cuentaAna, "2026-10-09", "DIARIA", -1));
        long deBeto = cliente.crearYObtenerId(beto, beto.presupuestoId(),
                cuerpo(cuentaBeto, "2026-10-09", "DIARIA", -1));

        cliente.generar(ana, ana.presupuestoId()).andExpect(jsonPath("$.generadas").value(2));

        assertThat(cliente.transacciones(beto, beto.presupuestoId()).get("totalElementos").asInt())
                .isZero();
        cliente.obtener(beto, beto.presupuestoId(), deBeto)
                .andExpect(jsonPath("$.proximaFecha").value("2026-10-09"));
    }
}
