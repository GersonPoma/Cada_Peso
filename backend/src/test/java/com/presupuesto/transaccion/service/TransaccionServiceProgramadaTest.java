package com.presupuesto.transaccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.presupuesto.beneficiario.entity.Beneficiario;
import com.presupuesto.beneficiario.service.BeneficiarioService;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.config.RelojDePrueba;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.dto.request.CrearTransaccionRequest;
import com.presupuesto.transaccion.dto.response.TransaccionResponse;
import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.repository.TransaccionRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
class TransaccionServiceProgramadaTest {

    private static final long PRESUPUESTO_ID = 20L;
    private static final long CUENTA_ID = 30L;
    private static final long CERRADA_ID = 32L;
    private static final long CATEGORIA_ID = 50L;
    private static final long PROGRAMADA_ID = 77L;
    private static final LocalDate OCURRENCIA = LocalDate.of(2026, 9, 5);

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

    private final Map<String, Beneficiario> beneficiarios = new HashMap<>();
    private TransaccionService service;
    private Presupuesto presupuesto;
    private Cuenta banco;
    private Cuenta cerrada;
    private Categoria comida;

    @BeforeEach
    void preparar() {
        service = new TransaccionService(
                transaccionRepository,
                new TransaccionReferencias(cuentaRepository, categoriaRepository),
                presupuestoService,
                beneficiarioService,
                new RelojDePrueba());
        when(beneficiarioService.obtenerOCrear(any(), anyString())).thenAnswer(inv -> {
            String nombre = inv.getArgument(1);
            return beneficiarios.computeIfAbsent(Beneficiario.normalizar(nombre), clave ->
                    Beneficiario.builder().nombre(nombre).nombreNormalizado(clave).build());
        });
        presupuesto = Presupuesto.builder().id(PRESUPUESTO_ID).build();
        banco = Cuenta.builder().id(CUENTA_ID).build();
        cerrada = Cuenta.builder().id(CERRADA_ID).build();
        cerrada.cerrar();
        comida = Categoria.builder().id(CATEGORIA_ID).build();
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(banco));
        when(cuentaRepository.findByIdAndPresupuestoId(CERRADA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(cerrada));
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(CATEGORIA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(comida));
        when(transaccionRepository.saveAndFlush(any(Transaccion.class))).then(returnsFirstArg());
    }

    private CrearTransaccionRequest solicitud(
            long cuentaId, Long categoriaId, String beneficiario) {
        return new CrearTransaccionRequest(
                cuentaId, OCURRENCIA, -4500L, categoriaId, beneficiario, "renta", false, List.of());
    }

    @Test
    void crearProgramadaGuardaLaOcurrenciaNoConciliadaYSinAprobarSinValidarUsuario() {
        Transaccion creada = service.crearProgramada(
                presupuesto, solicitud(CUENTA_ID, CATEGORIA_ID, null), PROGRAMADA_ID, OCURRENCIA);

        assertThat(creada.getProgramadaId()).isEqualTo(PROGRAMADA_ID);
        assertThat(creada.getFechaOcurrencia()).isEqualTo(OCURRENCIA);
        assertThat(creada.getFecha()).isEqualTo(OCURRENCIA);
        assertThat(creada.getMonto()).isEqualTo(-4500L);
        assertThat(creada.getMemo()).isEqualTo("renta");
        assertThat(creada.getCategoria()).isSameAs(comida);
        assertThat(creada.getEstado()).isEqualTo(EstadoTransaccion.NO_CONCILIADA);
        assertThat(creada.isAprobada()).isFalse();
        assertThat(TransaccionResponse.desde(creada).programadaId()).isEqualTo(PROGRAMADA_ID);
        verifyNoInteractions(presupuestoService);
    }

    @Test
    void crearProgramadaCreaElBeneficiarioYRecuerdaLaCategoria() {
        Transaccion creada = service.crearProgramada(
                presupuesto, solicitud(CUENTA_ID, CATEGORIA_ID, "Casero"), PROGRAMADA_ID,
                OCURRENCIA);

        Beneficiario beneficiario = beneficiarios.get("casero");
        assertThat(beneficiario).isNotNull();
        assertThat(creada.getBeneficiarioVinculado()).isSameAs(beneficiario);
        assertThat(creada.getBeneficiario()).isEqualTo("Casero");
        assertThat(beneficiario.getCategoriaPredeterminada()).isSameAs(comida);
    }

    @Test
    void crearProgramadaEnCuentaCerradaDa422YNoGuarda() {
        assertThrows(ReglaNegocioException.class, () -> service.crearProgramada(
                presupuesto, solicitud(CERRADA_ID, null, null), PROGRAMADA_ID, OCURRENCIA));
        verify(transaccionRepository, never()).saveAndFlush(any());
    }

    @Test
    void crearProgramadaConCategoriaDePagoDeTarjetaDa422() {
        Categoria pago = Categoria.builder().id(60L).cuentaTarjeta(banco).build();
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(60L, PRESUPUESTO_ID))
                .thenReturn(Optional.of(pago));

        assertThrows(ReglaNegocioException.class, () -> service.crearProgramada(
                presupuesto, solicitud(CUENTA_ID, 60L, null), PROGRAMADA_ID, OCURRENCIA));
        verify(transaccionRepository, never()).saveAndFlush(any());
    }

    @Test
    void exigirRegistrableAceptaCuentaAbiertaYCategoriaNulaORegular() {
        service.exigirRegistrable(banco, null);
        service.exigirRegistrable(banco, comida);
        service.exigirCategoriaRegistrable(null);
        service.exigirCategoriaRegistrable(comida);
    }

    @Test
    void exigirRegistrableRechazaCuentaCerradaYCategoriaDePago() {
        Categoria pago = Categoria.builder().id(60L).cuentaTarjeta(banco).build();

        assertThrows(ReglaNegocioException.class, () -> service.exigirRegistrable(cerrada, null));
        assertThrows(ReglaNegocioException.class, () -> service.exigirRegistrable(banco, pago));
        assertThrows(ReglaNegocioException.class, () -> service.exigirCategoriaRegistrable(pago));
    }

    @Test
    void crearManualNoGuardaVinculoConUnaPlantilla() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, 10L)).thenReturn(presupuesto);

        TransaccionResponse respuesta = service.crear(PRESUPUESTO_ID, 10L,
                new CrearTransaccionRequest(CUENTA_ID, OCURRENCIA, -1L, null, null, null, null,
                        null));

        assertThat(respuesta.programadaId()).isNull();
        assertThat(respuesta.aprobada()).isTrue();
        verify(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, 10L);
    }
}
