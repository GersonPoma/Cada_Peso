package com.presupuesto.categoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.entity.TipoGrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;

class MigracionPagosTarjetaTest {

    private static final String NOMBRE_GRUPO = "Pagos de tarjetas de crédito";

    /** Contra la base de datos (revierte al terminar), solo con presupuestos propios. */
    @Nested
    @SpringBootTest
    @Transactional
    class ConBaseDeDatos {

        @Autowired
        private MigracionPagosTarjeta migracion;

        @Autowired
        private GrupoCategoriaRepository grupoRepository;

        @Autowired
        private CategoriaRepository categoriaRepository;

        @Autowired
        private CuentaRepository cuentaRepository;

        @Autowired
        private PresupuestoRepository presupuestoRepository;

        @Autowired
        private UsuarioRepository usuarioRepository;

        private Usuario ana;
        private Presupuesto casa;
        private Presupuesto viajes;

        @BeforeEach
        void crearPresupuestos() {
            ana = usuarioRepository.saveAndFlush(Usuario.builder()
                    .email("ana-migracion@ejemplo.com").contrasena("hash").build());
            casa = presupuesto("Casa");
            viajes = presupuesto("Viajes");
        }

        @Test
        void unaTarjetaExistenteSinCategoriaRecibeElGrupoYSuCategoria() {
            Cuenta visa = tarjeta(casa, "Visa", false);

            migracion.migrar();

            Categoria categoria = categoriaRepository.findByCuentaTarjetaId(visa.getId())
                    .orElseThrow();
            assertThat(categoria.getNombre()).isEqualTo("Pago: Visa");
            assertThat(categoria.isOculta()).isFalse();
            assertThat(categoria.getGrupo().getTipo()).isEqualTo(TipoGrupoCategoria.PAGOS_TARJETA);
            assertThat(categoria.getGrupo().getNombre()).isEqualTo(NOMBRE_GRUPO);
        }

        @Test
        void unaTarjetaCerradaRecibeSuCategoriaOculta() {
            Cuenta vieja = tarjeta(casa, "Vieja", true);

            migracion.migrar();

            assertThat(categoriaRepository.findByCuentaTarjetaId(vieja.getId()).orElseThrow()
                    .isOculta()).isTrue();
        }

        @Test
        void unPresupuestoSinTarjetasNoCambia() {
            grupo(casa, "Vivienda", 0);
            cuenta(casa, "Banco", TipoCuenta.CORRIENTE, true);
            cuenta(casa, "Externa", TipoCuenta.TARJETA_CREDITO, false);

            migracion.migrar();

            assertThat(grupoRepository.findByPresupuestoIdOrderByOrden(casa.getId()))
                    .extracting(GrupoCategoria::getNombre).containsExactly("Vivienda");
            assertThat(categoriaRepository.findByGrupoPresupuestoIdOrderByOrden(casa.getId()))
                    .isEmpty();
        }

        @Test
        void unaSegundaEjecucionNoCreaNiCambiaNada() {
            Cuenta visa = tarjeta(casa, "Visa", false);
            Cuenta master = tarjeta(casa, "Master", true);
            migracion.migrar();
            List<Long> grupos = idsDeGrupos(casa);
            List<Long> categorias = idsDeCategorias(casa);
            Categoria antes = categoriaRepository.findByCuentaTarjetaId(master.getId())
                    .orElseThrow();
            String nombreAntes = antes.getNombre();

            migracion.migrar();

            assertThat(idsDeGrupos(casa)).isEqualTo(grupos);
            assertThat(idsDeCategorias(casa)).isEqualTo(categorias);
            assertThat(categoriaRepository.findByCuentaTarjetaId(master.getId()).orElseThrow()
                    .getNombre()).isEqualTo(nombreAntes);
            assertThat(categoriaRepository.findByCuentaTarjetaId(visa.getId())).isPresent();
            assertThat(categoriaRepository.tarjetasSinCategoria(casa.getId())).isEmpty();
        }

        @Test
        void lasFilasPreviasDeGruposConservanSuTipoNormalPorDefecto() {
            GrupoCategoria previo = grupo(casa, "Vivienda", 0);
            tarjeta(casa, "Visa", false);

            migracion.migrar();

            GrupoCategoria igual = grupoRepository.findById(previo.getId()).orElseThrow();
            assertThat(igual.getTipo()).isEqualTo(TipoGrupoCategoria.NORMAL);
            assertThat(igual.getNombre()).isEqualTo("Vivienda");
            assertThat(grupoRepository.findByPresupuestoIdOrderByOrden(casa.getId()))
                    .extracting(GrupoCategoria::getTipo)
                    .containsExactly(TipoGrupoCategoria.NORMAL, TipoGrupoCategoria.PAGOS_TARJETA);
        }

        @Test
        void conUnGrupoNormalConElNombreDelGrupoDePagosCreaUnSoloGrupoConNombreAlterno() {
            GrupoCategoria normal = grupo(casa, NOMBRE_GRUPO, 0);
            Cuenta visa = tarjeta(casa, "Visa", false);
            Cuenta master = tarjeta(casa, "Master", false);

            migracion.migrar();
            migracion.migrar();

            List<GrupoCategoria> grupos = grupoRepository.findByPresupuestoIdOrderByOrden(
                    casa.getId());
            assertThat(grupos).hasSize(2);
            assertThat(grupos.get(0).getId()).isEqualTo(normal.getId());
            assertThat(grupos.get(0).getTipo()).isEqualTo(TipoGrupoCategoria.NORMAL);
            assertThat(grupos.get(0).getNombre()).isEqualTo(NOMBRE_GRUPO);
            assertThat(grupos.get(1).getTipo()).isEqualTo(TipoGrupoCategoria.PAGOS_TARJETA);
            assertThat(grupos.get(1).getNombre()).isEqualTo(NOMBRE_GRUPO + " (2)");
            assertThat(categoriaRepository.findByCuentaTarjetaId(visa.getId()).orElseThrow()
                    .getGrupo().getId()).isEqualTo(grupos.get(1).getId());
            assertThat(categoriaRepository.findByCuentaTarjetaId(master.getId()).orElseThrow()
                    .getGrupo().getId()).isEqualTo(grupos.get(1).getId());
        }

        @Test
        void unaTarjetaConNombreDe100CaracteresRecibeUnaCategoriaDeA100() {
            Cuenta larga = tarjeta(casa, "a".repeat(100), false);

            migracion.migrar();

            assertThat(categoriaRepository.findByCuentaTarjetaId(larga.getId()).orElseThrow()
                    .getNombre()).hasSize(100).startsWith("Pago: aaa");
        }

        @Test
        void cadaPresupuestoRecibeSuPropioGrupoSinMezclarse() {
            Cuenta deCasa = tarjeta(casa, "Visa", false);
            Cuenta deViajes = tarjeta(viajes, "Visa", false);

            migracion.migrar();

            Categoria a = categoriaRepository.findByCuentaTarjetaId(deCasa.getId()).orElseThrow();
            Categoria b = categoriaRepository.findByCuentaTarjetaId(deViajes.getId())
                    .orElseThrow();
            assertThat(a.getGrupo().getPresupuesto().getId()).isEqualTo(casa.getId());
            assertThat(b.getGrupo().getPresupuesto().getId()).isEqualTo(viajes.getId());
            assertThat(a.getGrupo().getId()).isNotEqualTo(b.getGrupo().getId());
        }

        private List<Long> idsDeGrupos(Presupuesto presupuesto) {
            return grupoRepository.findByPresupuestoIdOrderByOrden(presupuesto.getId()).stream()
                    .map(GrupoCategoria::getId).toList();
        }

        private List<Long> idsDeCategorias(Presupuesto presupuesto) {
            return categoriaRepository.findByGrupoPresupuestoIdOrderByOrden(presupuesto.getId())
                    .stream().map(Categoria::getId).toList();
        }

        private Presupuesto presupuesto(String nombre) {
            return presupuestoRepository.saveAndFlush(Presupuesto.builder()
                    .usuario(ana).nombre(nombre)
                    .nombreNormalizado(Presupuesto.normalizar(nombre)).moneda("BOB").build());
        }

        private GrupoCategoria grupo(Presupuesto presupuesto, String nombre, int orden) {
            return grupoRepository.saveAndFlush(GrupoCategoria.builder()
                    .presupuesto(presupuesto)
                    .nombre(nombre)
                    .nombreNormalizado(GrupoCategoria.normalizar(nombre))
                    .orden(orden)
                    .build());
        }

        private Cuenta tarjeta(Presupuesto presupuesto, String nombre, boolean cerrada) {
            Cuenta cuenta = cuenta(presupuesto, nombre, TipoCuenta.TARJETA_CREDITO, true);
            if (cerrada) {
                cuenta.cerrar();
                cuentaRepository.saveAndFlush(cuenta);
            }
            return cuenta;
        }

        private Cuenta cuenta(
                Presupuesto presupuesto, String nombre, TipoCuenta tipo, boolean enPresupuesto) {
            return cuentaRepository.saveAndFlush(Cuenta.builder()
                    .presupuesto(presupuesto)
                    .nombre(nombre)
                    .nombreNormalizado(Cuenta.normalizar(nombre))
                    .tipo(tipo)
                    .enPresupuesto(enPresupuesto)
                    .build());
        }
    }

    /**
     * Garantía (d) sin concurrencia real: se simula la violación de la restricción única en un
     * presupuesto y se comprueba que cada presupuesto va en su propia transacción.
     */
    @Nested
    class FalloPorPresupuesto {

        private CategoriaRepository categoriaRepository;
        private CategoriasPagoTarjeta categorias;
        private TransaccionesRegistradas transacciones;
        private MigracionPagosTarjeta migracion;
        private Cuenta tarjetaUno;
        private Cuenta tarjetaDos;

        @BeforeEach
        void preparar() {
            categoriaRepository = mock(CategoriaRepository.class);
            categorias = mock(CategoriasPagoTarjeta.class);
            transacciones = new TransaccionesRegistradas();
            migracion = new MigracionPagosTarjeta(categoriaRepository, categorias, transacciones);
            tarjetaUno = Cuenta.builder().id(1L).nombre("Uno").build();
            tarjetaDos = Cuenta.builder().id(2L).nombre("Dos").build();
            when(categoriaRepository.presupuestosConTarjetasSinCategoria())
                    .thenReturn(List.of(10L, 20L));
            when(categoriaRepository.tarjetasSinCategoria(10L)).thenReturn(List.of(tarjetaUno));
            when(categoriaRepository.tarjetasSinCategoria(20L)).thenReturn(List.of(tarjetaDos));
        }

        @Test
        void elFalloDeUnPresupuestoNoTumbaElArranqueNiImpideMigrarLosDemas() {
            doThrow(new DataIntegrityViolationException("uk_categorias_cuenta_tarjeta"))
                    .when(categorias).crear(tarjetaUno);

            migracion.migrar();

            verify(categorias).crear(tarjetaDos);
            assertThat(transacciones.eventos).containsExactly("rollback", "commit");
        }

        @Test
        void laSiguienteEjecucionCompletaLoQueFalto() {
            doThrow(new DataIntegrityViolationException("uk_categorias_cuenta_tarjeta"))
                    .doReturn(null)
                    .when(categorias).crear(tarjetaUno);

            migracion.migrar();
            migracion.migrar();

            verify(categorias, times(2)).crear(tarjetaUno);
            assertThat(transacciones.eventos)
                    .containsExactly("rollback", "commit", "commit", "commit");
        }

        @Test
        void sinPendientesNoAbreNingunaTransaccionNiCreaNada() {
            when(categoriaRepository.presupuestosConTarjetasSinCategoria()).thenReturn(List.of());

            migracion.migrar();

            assertThat(transacciones.eventos).isEmpty();
            verify(categorias, times(0)).crear(tarjetaUno);
        }
    }

    /** Registra cómo termina cada transacción, sin base de datos. */
    static class TransaccionesRegistradas implements PlatformTransactionManager {

        final List<String> eventos = new ArrayList<>();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definicion)
                throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus estado) throws TransactionException {
            eventos.add("commit");
        }

        @Override
        public void rollback(TransactionStatus estado) throws TransactionException {
            eventos.add("rollback");
        }
    }
}
