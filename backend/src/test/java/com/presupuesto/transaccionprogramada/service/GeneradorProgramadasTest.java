package com.presupuesto.transaccionprogramada.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import com.presupuesto.transaccion.service.TransaccionService;
import com.presupuesto.transaccionprogramada.entity.FrecuenciaProgramada;
import com.presupuesto.transaccionprogramada.entity.TransaccionProgramada;
import com.presupuesto.transaccionprogramada.repository.TransaccionProgramadaRepository;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class GeneradorProgramadasTest {

    private static LocalDate f(int anio, int mes, int dia) {
        return LocalDate.of(anio, mes, dia);
    }

    /**
     * Contra la base de datos real y SIN transacción de prueba: el generador abre las suyas y
     * guarda el error en una transacción aparte, así que los datos deben estar confirmados. Cada
     * prueba crea sus propios presupuestos y los borra al terminar; el generador solo se llama
     * por presupuesto, nunca sobre los datos de otras personas.
     */
    @Nested
    @SpringBootTest
    class ConBaseDeDatos {

        @Autowired
        private GeneradorProgramadas generador;

        @Autowired
        private TransaccionProgramadaRepository programadaRepository;

        @Autowired
        private TransaccionRepository transaccionRepository;

        @Autowired
        private CuentaRepository cuentaRepository;

        @Autowired
        private CategoriaRepository categoriaRepository;

        @Autowired
        private GrupoCategoriaRepository grupoRepository;

        @Autowired
        private PresupuestoRepository presupuestoRepository;

        @Autowired
        private UsuarioRepository usuarioRepository;

        @Autowired
        private JdbcTemplate jdbc;

        private Usuario ana;
        private Presupuesto casa;
        private Cuenta banco;
        private final List<Long> presupuestos = new ArrayList<>();

        @BeforeEach
        void crearDatos() {
            ana = usuarioRepository.saveAndFlush(Usuario.builder()
                    .email("generador-" + UUID.randomUUID() + "@ejemplo.com")
                    .contrasena("hash").build());
            casa = presupuesto("Casa");
            banco = cuenta(casa, "Banco");
        }

        @AfterEach
        void limpiar() {
            for (Long id : presupuestos) {
                jdbc.update("delete from transacciones where cuenta_id in "
                        + "(select id from cuentas where presupuesto_id = ?)", id);
                jdbc.update("delete from transacciones_programadas where presupuesto_id = ?", id);
                jdbc.update("delete from beneficiarios where presupuesto_id = ?", id);
                jdbc.update("delete from categorias where grupo_id in "
                        + "(select id from grupos_categoria where presupuesto_id = ?)", id);
                jdbc.update("delete from grupos_categoria where presupuesto_id = ?", id);
                jdbc.update("delete from cuentas where presupuesto_id = ?", id);
                jdbc.update("delete from presupuestos where id = ?", id);
            }
            jdbc.update("delete from usuarios where id = ?", ana.getId());
        }

        private Presupuesto presupuesto(String nombre) {
            Presupuesto creado = presupuestoRepository.saveAndFlush(Presupuesto.builder()
                    .usuario(ana).nombre(nombre)
                    .nombreNormalizado(Presupuesto.normalizar(nombre)).moneda("BOB").build());
            presupuestos.add(creado.getId());
            return creado;
        }

        private Cuenta cuenta(Presupuesto presupuesto, String nombre) {
            return cuentaRepository.saveAndFlush(Cuenta.builder()
                    .presupuesto(presupuesto).nombre(nombre)
                    .nombreNormalizado(Cuenta.normalizar(nombre))
                    .tipo(TipoCuenta.CORRIENTE).build());
        }

        private Categoria categoria(Presupuesto presupuesto, String nombre) {
            GrupoCategoria grupo = grupoRepository.saveAndFlush(GrupoCategoria.builder()
                    .presupuesto(presupuesto).nombre("Grupo " + nombre)
                    .nombreNormalizado(GrupoCategoria.normalizar("Grupo " + nombre))
                    .orden(0).build());
            return categoriaRepository.saveAndFlush(Categoria.builder()
                    .grupo(grupo).nombre(nombre).nombreNormalizado(Categoria.normalizar(nombre))
                    .orden(0).build());
        }

        private TransaccionProgramada plantilla(
                Cuenta cuenta, FrecuenciaProgramada frecuencia, LocalDate inicio,
                LocalDate fin) {
            return programadaRepository.saveAndFlush(TransaccionProgramada.builder()
                    .presupuesto(cuenta.getPresupuesto()).cuenta(cuenta)
                    .fechaInicio(inicio).frecuencia(frecuencia).fechaFin(fin)
                    .monto(-5000L).proximaFecha(inicio).build());
        }

        private TransaccionProgramada recargar(TransaccionProgramada programada) {
            return programadaRepository.findById(programada.getId()).orElseThrow();
        }

        private List<LocalDate> fechasGeneradas(TransaccionProgramada programada) {
            return jdbc.queryForList("select fecha from transacciones "
                    + "where programada_id = ? order by fecha", LocalDate.class,
                    programada.getId());
        }

        private ResultadoGeneracion generar(Presupuesto presupuesto, LocalDate hasta) {
            return generador.generarVencidas(presupuesto.getId(), hasta);
        }

        @Test
        void unaOcurrenciaVencidaCreaLaTransaccionNoConciliadaYSinAprobar() {
            TransaccionProgramada programada = plantilla(
                    banco, FrecuenciaProgramada.MENSUAL, f(2026, 10, 5), null);

            ResultadoGeneracion resultado = generar(casa, f(2026, 10, 5));

            assertThat(resultado).isEqualTo(new ResultadoGeneracion(1, 0));
            List<Transaccion> creadas = transaccionRepository.findAll().stream()
                    .filter(t -> programada.getId().equals(t.getProgramadaId())).toList();
            assertThat(creadas).hasSize(1);
            Transaccion creada = creadas.get(0);
            assertThat(creada.getFecha()).isEqualTo(f(2026, 10, 5));
            assertThat(creada.getFechaOcurrencia()).isEqualTo(f(2026, 10, 5));
            assertThat(creada.getMonto()).isEqualTo(-5000L);
            assertThat(creada.getEstado()).isEqualTo(EstadoTransaccion.NO_CONCILIADA);
            assertThat(creada.isAprobada()).isFalse();
            TransaccionProgramada despues = recargar(programada);
            assertThat(despues.getProximaFecha()).isEqualTo(f(2026, 11, 5));
            assertThat(despues.getUltimaOcurrenciaGenerada()).isEqualTo(f(2026, 10, 5));
            assertThat(despues.getUltimoError()).isNull();
        }

        @Test
        void unaOcurrenciaFuturaNoGeneraNada() {
            TransaccionProgramada programada = plantilla(
                    banco, FrecuenciaProgramada.MENSUAL, f(2026, 10, 6), null);

            ResultadoGeneracion resultado = generar(casa, f(2026, 10, 5));

            assertThat(resultado).isEqualTo(new ResultadoGeneracion(0, 0));
            assertThat(fechasGeneradas(programada)).isEmpty();
            assertThat(recargar(programada).getProximaFecha()).isEqualTo(f(2026, 10, 6));
        }

        @Test
        void lasOcurrenciasPerdidasSeGeneranTodasConSuFechaOriginal() {
            TransaccionProgramada programada = plantilla(
                    banco, FrecuenciaProgramada.SEMANAL, f(2026, 9, 12), null);

            ResultadoGeneracion resultado = generar(casa, f(2026, 10, 10));

            assertThat(resultado.generadas()).isEqualTo(5);
            assertThat(fechasGeneradas(programada)).containsExactly(
                    f(2026, 9, 12), f(2026, 9, 19), f(2026, 9, 26), f(2026, 10, 3),
                    f(2026, 10, 10));
            assertThat(recargar(programada).getProximaFecha()).isEqualTo(f(2026, 10, 17));
        }

        @Test
        void conMasDe366VencidasGeneraLasMasAntiguasYElRestoEnLaSiguienteEjecucion() {
            LocalDate hasta = f(2026, 10, 10);
            LocalDate inicio = hasta.minusDays(399);
            TransaccionProgramada programada = plantilla(
                    banco, FrecuenciaProgramada.DIARIA, inicio, null);

            ResultadoGeneracion primera = generar(casa, hasta);

            assertThat(primera.generadas()).isEqualTo(366);
            assertThat(fechasGeneradas(programada)).hasSize(366)
                    .first().isEqualTo(inicio);
            assertThat(fechasGeneradas(programada)).last().isEqualTo(inicio.plusDays(365));
            assertThat(recargar(programada).getProximaFecha()).isEqualTo(inicio.plusDays(366));

            ResultadoGeneracion segunda = generar(casa, hasta);

            assertThat(segunda.generadas()).isEqualTo(34);
            assertThat(fechasGeneradas(programada)).hasSize(400).last().isEqualTo(hasta);
            assertThat(recargar(programada).getProximaFecha()).isEqualTo(hasta.plusDays(1));
        }

        @Test
        void unaMensualDesdeElDia31NoSeDegradaALo28() {
            TransaccionProgramada programada = plantilla(
                    banco, FrecuenciaProgramada.MENSUAL, f(2027, 1, 31), null);

            generar(casa, f(2027, 4, 30));

            assertThat(fechasGeneradas(programada)).containsExactly(
                    f(2027, 1, 31), f(2027, 2, 28), f(2027, 3, 31), f(2027, 4, 30));
            assertThat(recargar(programada).getProximaFecha()).isEqualTo(f(2027, 5, 31));
        }

        @Test
        void unaMensualDesdeElDia31EnAnioBisiestoGeneraElDia29DeFebrero() {
            TransaccionProgramada programada = plantilla(
                    banco, FrecuenciaProgramada.MENSUAL, f(2028, 1, 31), null);

            generar(casa, f(2028, 3, 31));

            assertThat(fechasGeneradas(programada)).containsExactly(
                    f(2028, 1, 31), f(2028, 2, 29), f(2028, 3, 31));
        }

        @Test
        void laFechaDeFinDetieneLaGeneracionYDejaLaProximaFechaNula() {
            TransaccionProgramada programada = plantilla(
                    banco, FrecuenciaProgramada.SEMANAL, f(2026, 9, 28), f(2026, 10, 5));

            generar(casa, f(2026, 10, 20));

            assertThat(fechasGeneradas(programada)).containsExactly(
                    f(2026, 9, 28), f(2026, 10, 5));
            TransaccionProgramada despues = recargar(programada);
            assertThat(despues.getProximaFecha()).isNull();
            assertThat(despues.getUltimaOcurrenciaGenerada()).isEqualTo(f(2026, 10, 5));
            // Una plantilla finalizada ya no se procesa.
            assertThat(generar(casa, f(2026, 12, 31))).isEqualTo(new ResultadoGeneracion(0, 0));
        }

        @Test
        void unaPlantillaPausadaNoGeneraNada() {
            TransaccionProgramada programada = plantilla(
                    banco, FrecuenciaProgramada.DIARIA, f(2026, 10, 1), null);
            programada.pausar();
            programadaRepository.saveAndFlush(programada);

            ResultadoGeneracion resultado = generar(casa, f(2026, 10, 10));

            assertThat(resultado).isEqualTo(new ResultadoGeneracion(0, 0));
            assertThat(fechasGeneradas(programada)).isEmpty();
        }

        @Test
        void correrDosVecesConLaMismaFechaNoDuplicaNada() {
            TransaccionProgramada programada = plantilla(
                    banco, FrecuenciaProgramada.SEMANAL, f(2026, 9, 12), null);

            generar(casa, f(2026, 10, 10));
            ResultadoGeneracion segunda = generar(casa, f(2026, 10, 10));

            assertThat(segunda).isEqualTo(new ResultadoGeneracion(0, 0));
            assertThat(fechasGeneradas(programada)).hasSize(5);
        }

        @Test
        void unaOcurrenciaQueYaExisteNoSeDuplicaYLaProximaFechaAvanza() {
            TransaccionProgramada programada = plantilla(
                    banco, FrecuenciaProgramada.MENSUAL, f(2026, 10, 5), null);
            transaccionRepository.saveAndFlush(Transaccion.builder()
                    .cuenta(banco).fecha(f(2026, 10, 5)).monto(-5000L)
                    .programadaId(programada.getId()).fechaOcurrencia(f(2026, 10, 5)).build());

            ResultadoGeneracion resultado = generar(casa, f(2026, 10, 5));

            assertThat(resultado).isEqualTo(new ResultadoGeneracion(0, 0));
            assertThat(fechasGeneradas(programada)).containsExactly(f(2026, 10, 5));
            assertThat(recargar(programada).getProximaFecha()).isEqualTo(f(2026, 11, 5));
        }

        @Test
        void unaOcurrenciaBorradaNoReapareceYLaProximaFechaNoRetrocede() {
            TransaccionProgramada programada = plantilla(
                    banco, FrecuenciaProgramada.MENSUAL, f(2026, 10, 5), null);
            generar(casa, f(2026, 10, 5));
            Long generada = jdbc.queryForObject(
                    "select id from transacciones where programada_id = ?", Long.class,
                    programada.getId());
            transaccionRepository.deleteById(generada);

            ResultadoGeneracion misma = generar(casa, f(2026, 10, 5));
            ResultadoGeneracion posterior = generar(casa, f(2026, 10, 20));

            assertThat(misma).isEqualTo(new ResultadoGeneracion(0, 0));
            assertThat(posterior).isEqualTo(new ResultadoGeneracion(0, 0));
            assertThat(fechasGeneradas(programada)).isEmpty();
            assertThat(recargar(programada).getProximaFecha()).isEqualTo(f(2026, 11, 5));
        }

        @Test
        void laTransaccionGeneradaCreaElBeneficiarioYUsaLaCategoria() {
            Categoria renta = categoria(casa, "Renta");
            TransaccionProgramada programada = programadaRepository.saveAndFlush(
                    TransaccionProgramada.builder()
                            .presupuesto(casa).cuenta(banco).fechaInicio(f(2026, 10, 5))
                            .frecuencia(FrecuenciaProgramada.MENSUAL).monto(-5000L)
                            .categoria(renta).beneficiario("Casero").memo("alquiler")
                            .proximaFecha(f(2026, 10, 5)).build());

            generar(casa, f(2026, 10, 5));

            Transaccion creada = transaccionRepository.findAll().stream()
                    .filter(t -> programada.getId().equals(t.getProgramadaId()))
                    .findFirst().orElseThrow();
            assertThat(creada.getCategoria().getId()).isEqualTo(renta.getId());
            assertThat(creada.getBeneficiario()).isEqualTo("Casero");
            assertThat(creada.getBeneficiarioVinculado()).isNotNull();
            assertThat(creada.getMemo()).isEqualTo("alquiler");
            assertThat(jdbc.queryForObject("select count(*) from beneficiarios "
                    + "where presupuesto_id = ?", Integer.class, casa.getId())).isEqualTo(1);
        }

        @Test
        void elFalloDeUnaPlantillaNoAfectaALasDemasYQuedaVisible() {
            Cuenta ahorros = cuenta(casa, "Ahorros");
            TransaccionProgramada buena = plantilla(
                    banco, FrecuenciaProgramada.SEMANAL, f(2026, 10, 1), null);
            TransaccionProgramada mala = plantilla(
                    ahorros, FrecuenciaProgramada.SEMANAL, f(2026, 10, 1), null);
            ahorros.cerrar();
            cuentaRepository.saveAndFlush(ahorros);

            ResultadoGeneracion resultado = generar(casa, f(2026, 10, 10));

            assertThat(resultado).isEqualTo(new ResultadoGeneracion(2, 1));
            assertThat(fechasGeneradas(buena)).containsExactly(f(2026, 10, 1), f(2026, 10, 8));
            assertThat(recargar(buena).getUltimoError()).isNull();
            assertThat(fechasGeneradas(mala)).isEmpty();
            TransaccionProgramada conError = recargar(mala);
            assertThat(conError.getUltimoError()).contains("cerrada");
            assertThat(conError.getProximaFecha()).isEqualTo(f(2026, 10, 1));
            assertThat(conError.isActiva()).isTrue();
        }

        @Test
        void unaPlantillaConErrorSeRecuperaCuandoSeReabreLaCuenta() {
            Cuenta ahorros = cuenta(casa, "Ahorros");
            TransaccionProgramada programada = plantilla(
                    ahorros, FrecuenciaProgramada.SEMANAL, f(2026, 10, 1), null);
            ahorros.cerrar();
            cuentaRepository.saveAndFlush(ahorros);
            generar(casa, f(2026, 10, 10));
            assertThat(recargar(programada).getUltimoError()).isNotNull();

            ahorros.reabrir();
            cuentaRepository.saveAndFlush(ahorros);
            ResultadoGeneracion resultado = generar(casa, f(2026, 10, 10));

            assertThat(resultado).isEqualTo(new ResultadoGeneracion(2, 0));
            TransaccionProgramada despues = recargar(programada);
            assertThat(despues.getUltimoError()).isNull();
            assertThat(despues.getProximaFecha()).isEqualTo(f(2026, 10, 15));
            assertThat(fechasGeneradas(programada)).hasSize(2);
        }

        @Test
        void unFalloAMitadDeLasOcurrenciasNoDejaTransaccionesAMedias() {
            Categoria renta = categoria(casa, "Renta");
            TransaccionProgramada programada = programadaRepository.saveAndFlush(
                    TransaccionProgramada.builder()
                            .presupuesto(casa).cuenta(banco).fechaInicio(f(2026, 10, 1))
                            .frecuencia(FrecuenciaProgramada.DIARIA).monto(-5000L)
                            .categoria(renta).proximaFecha(f(2026, 10, 1)).build());
            // La categoría pasa a ser de pago de tarjeta: toda ocurrencia será rechazada.
            jdbc.update("update categorias set cuenta_tarjeta_id = ? where id = ?",
                    banco.getId(), renta.getId());

            ResultadoGeneracion resultado = generar(casa, f(2026, 10, 3));

            assertThat(resultado).isEqualTo(new ResultadoGeneracion(0, 1));
            assertThat(fechasGeneradas(programada)).isEmpty();
            assertThat(recargar(programada).getUltimoError()).isNotBlank();
            assertThat(recargar(programada).getProximaFecha()).isEqualTo(f(2026, 10, 1));
        }

        @Test
        void soloProcesaElPresupuestoIndicado() {
            Presupuesto viajes = presupuesto("Viajes");
            Cuenta efectivo = cuenta(viajes, "Efectivo");
            TransaccionProgramada deCasa = plantilla(
                    banco, FrecuenciaProgramada.SEMANAL, f(2026, 10, 1), null);
            TransaccionProgramada deViajes = plantilla(
                    efectivo, FrecuenciaProgramada.SEMANAL, f(2026, 10, 1), null);

            ResultadoGeneracion resultado = generar(casa, f(2026, 10, 10));

            assertThat(resultado.generadas()).isEqualTo(2);
            assertThat(fechasGeneradas(deCasa)).hasSize(2);
            assertThat(fechasGeneradas(deViajes)).isEmpty();
            assertThat(recargar(deViajes).getProximaFecha()).isEqualTo(f(2026, 10, 1));
        }
    }

    /** Sin base de datos: la lógica de aislamiento de fallos con colaboradores simulados. */
    @Nested
    class ConColaboradoresSimulados {

        private TransaccionProgramadaRepository programadaRepository;
        private GeneradorProgramadas generador;

        @BeforeEach
        void preparar() {
            programadaRepository = mock(TransaccionProgramadaRepository.class);
            generador = new GeneradorProgramadas(
                    programadaRepository,
                    mock(TransaccionRepository.class),
                    mock(TransaccionService.class),
                    mock(PlatformTransactionManager.class));
        }

        @Test
        void siFallaGuardarElErrorSeRegistraYSeSigueConLasDemasPlantillas() {
            when(programadaRepository.idsVencidas(f(2026, 10, 10))).thenReturn(List.of(1L, 2L));
            when(programadaRepository.bloquear(1L))
                    .thenThrow(new ReglaNegocioException("La cuenta está cerrada"));
            // Al guardar el error de la primera, la base falla.
            when(programadaRepository.findById(1L)).thenThrow(new IllegalStateException("caída"));
            when(programadaRepository.bloquear(2L)).thenReturn(Optional.empty());

            ResultadoGeneracion resultado = generador.generarVencidas(f(2026, 10, 10));

            assertThat(resultado).isEqualTo(new ResultadoGeneracion(0, 1));
            verify(programadaRepository).bloquear(2L);
        }

        @Test
        void unaPlantillaInexistenteOSinVencerNoCreaNiFalla() {
            TransaccionProgramada futura = TransaccionProgramada.builder()
                    .id(3L).proximaFecha(f(2026, 10, 11)).build();
            when(programadaRepository.idsVencidas(f(2026, 10, 10))).thenReturn(List.of(3L, 4L));
            when(programadaRepository.bloquear(3L)).thenReturn(Optional.of(futura));
            when(programadaRepository.bloquear(4L)).thenReturn(Optional.empty());

            ResultadoGeneracion resultado = generador.generarVencidas(f(2026, 10, 10));

            assertThat(resultado).isEqualTo(new ResultadoGeneracion(0, 0));
            verify(programadaRepository, never()).saveAndFlush(any());
            verify(programadaRepository, never()).findById(anyLong());
        }

        @Test
        void sinPlantillasVencidasNoHaceNada() {
            when(programadaRepository.idsVencidas(f(2026, 10, 10))).thenReturn(List.of());

            assertThat(generador.generarVencidas(f(2026, 10, 10)))
                    .isEqualTo(new ResultadoGeneracion(0, 0));
        }
    }
}
