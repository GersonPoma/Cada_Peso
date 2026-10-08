package com.presupuesto.transaccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.beneficiario.entity.Beneficiario;
import com.presupuesto.beneficiario.service.BeneficiarioService;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.config.RelojDePrueba;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransaccionServiceConciliacionTest {

    private static final long PRESUPUESTO_ID = 20L;
    private static final long CUENTA_ID = 30L;
    private static final long CATEGORIA_ID = 50L;
    private static final LocalDate FECHA = LocalDate.of(2026, 9, 10);

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private PresupuestoService presupuestoService;

    @Mock
    private BeneficiarioService beneficiarioService;

    private TransaccionService service;
    private Presupuesto presupuesto;
    private Cuenta banco;
    private Categoria comida;
    private Beneficiario beneficiarioAjuste;

    @BeforeEach
    void preparar() {
        service = new TransaccionService(
                transaccionRepository,
                new TransaccionReferencias(cuentaRepository, categoriaRepository),
                presupuestoService,
                beneficiarioService,
                new RelojDePrueba());
        presupuesto = Presupuesto.builder().id(PRESUPUESTO_ID).build();
        banco = Cuenta.builder().id(CUENTA_ID).build();
        comida = Categoria.builder().id(CATEGORIA_ID).build();
        beneficiarioAjuste = Beneficiario.builder()
                .nombre(TransaccionService.BENEFICIARIO_AJUSTE)
                .nombreNormalizado("ajuste de conciliación")
                .build();
        when(beneficiarioService.obtenerOCrear(any(), anyString())).thenReturn(beneficiarioAjuste);
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(CATEGORIA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(comida));
        when(transaccionRepository.saveAndFlush(any(Transaccion.class))).then(returnsFirstArg());
    }

    @Test
    void elAjusteEsConciliadoAprobadoYConElBeneficiarioFijo() {
        Transaccion ajuste = service.crearAjuste(presupuesto, banco, FECHA, 10_000L, null);

        assertThat(ajuste.getCuenta()).isSameAs(banco);
        assertThat(ajuste.getFecha()).isEqualTo(FECHA);
        assertThat(ajuste.getMonto()).isEqualTo(10_000L);
        assertThat(ajuste.getCategoria()).isNull();
        assertThat(ajuste.getEstado()).isEqualTo(EstadoTransaccion.CONCILIADA);
        assertThat(ajuste.isAprobada()).isTrue();
        assertThat(ajuste.getBeneficiario()).isEqualTo("Ajuste de conciliación");
        assertThat(ajuste.getBeneficiarioVinculado()).isSameAs(beneficiarioAjuste);
        verify(beneficiarioService).obtenerOCrear(presupuesto, "Ajuste de conciliación");
    }

    @Test
    void elAjusteNoRecuerdaLaCategoriaEnElBeneficiario() {
        Transaccion ajuste = service.crearAjuste(presupuesto, banco, FECHA, -10_000L, CATEGORIA_ID);

        assertThat(ajuste.getCategoria()).isSameAs(comida);
        assertThat(beneficiarioAjuste.getCategoriaPredeterminada()).isNull();
    }

    @Test
    void unaCategoriaInexistenteDa404() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.crearAjuste(presupuesto, banco, FECHA, -10_000L, 999L));
    }

    @Test
    void reconciliarHastaUsaElRelojYDevuelveLaCantidad() {
        Instant ahora = new RelojDePrueba().instant();
        when(transaccionRepository.reconciliarConciliadasHasta(CUENTA_ID, FECHA, ahora))
                .thenReturn(3);

        assertThat(service.reconciliarHasta(CUENTA_ID, FECHA)).isEqualTo(3);
    }
}
