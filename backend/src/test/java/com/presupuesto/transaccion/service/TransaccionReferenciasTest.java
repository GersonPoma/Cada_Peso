package com.presupuesto.transaccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.NegocioException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.transaccion.dto.request.SubTransaccionRequest;
import java.util.ArrayList;
import java.util.List;
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
class TransaccionReferenciasTest {

    private static final long PRESUPUESTO_ID = 20L;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    private TransaccionReferencias referencias;
    private Categoria comida;
    private Categoria oculta;

    @BeforeEach
    void preparar() {
        referencias = new TransaccionReferencias(cuentaRepository, categoriaRepository);
        comida = Categoria.builder().id(1L).build();
        oculta = Categoria.builder().id(2L).build();
        oculta.ocultar();
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(1L, PRESUPUESTO_ID))
                .thenReturn(Optional.of(comida));
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(2L, PRESUPUESTO_ID))
                .thenReturn(Optional.of(oculta));
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(99L, PRESUPUESTO_ID))
                .thenReturn(Optional.empty());
    }

    @Test
    void cuentaDelPresupuestoSeDevuelveYAjenaDa404() {
        Cuenta banco = Cuenta.builder().id(5L).build();
        when(cuentaRepository.findByIdAndPresupuestoId(5L, PRESUPUESTO_ID))
                .thenReturn(Optional.of(banco));
        when(cuentaRepository.findByIdAndPresupuestoId(6L, PRESUPUESTO_ID))
                .thenReturn(Optional.empty());

        assertThat(referencias.cuenta(5L, PRESUPUESTO_ID)).isSameAs(banco);
        assertThrows(RecursoNoEncontradoException.class,
                () -> referencias.cuenta(6L, PRESUPUESTO_ID));
    }

    @Test
    void categoriaNulaDaNulaOcultaSeAceptaYAjenaDa404() {
        assertThat(referencias.categoria(null, PRESUPUESTO_ID)).isNull();
        assertThat(referencias.categoria(2L, PRESUPUESTO_ID)).isSameAs(oculta);
        assertThrows(RecursoNoEncontradoException.class,
                () -> referencias.categoria(99L, PRESUPUESTO_ID));
    }

    @Test
    void exigirAbiertaRechazaUnaCuentaCerradaCon422() {
        Cuenta cerrada = Cuenta.builder().build();
        cerrada.cerrar();

        referencias.exigirAbierta(Cuenta.builder().build());
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> referencias.exigirAbierta(cerrada));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
    }

    @Test
    void sinSubtransaccionesSoloResuelveLaCategoria() {
        TransaccionReferencias.Division division =
                referencias.dividir(PRESUPUESTO_ID, 1L, -1000L, List.of());

        assertThat(division.categoria()).isSameAs(comida);
        assertThat(division.partes()).isEmpty();
    }

    @Test
    void unaDivisionConSumaCorrectaArmaLasPartesYNoLlevaCategoriaPropia() {
        TransaccionReferencias.Division division = referencias.dividir(
                PRESUPUESTO_ID, null, -3000L,
                List.of(parte(1L, -1000L), parte(2L, -1500L), parte(null, -500L)));

        assertThat(division.categoria()).isNull();
        assertThat(division.partes()).hasSize(3);
        assertThat(division.partes().get(0).getCategoria()).isSameAs(comida);
        assertThat(division.partes().get(1).getCategoria()).isSameAs(oculta);
        assertThat(division.partes().get(2).getCategoria()).isNull();
    }

    @Test
    void laCategoriaRepetidaSeConsultaUnaSolaVez() {
        referencias.dividir(PRESUPUESTO_ID, null, -2000L,
                List.of(parte(1L, -1000L), parte(1L, -1000L)));

        verify(categoriaRepository, times(1))
                .findByIdAndGrupoPresupuestoId(1L, PRESUPUESTO_ID);
    }

    @Test
    void unaSolaSubtransaccionDa400() {
        DatosInvalidosException error = assertThrows(DatosInvalidosException.class,
                () -> referencias.dividir(
                        PRESUPUESTO_ID, null, -1000L, List.of(parte(null, -1000L))));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.DATOS_INVALIDOS);
    }

    @Test
    void veinteSonValidasYVeintiunaDa400() {
        assertThat(referencias.dividir(PRESUPUESTO_ID, null, -20L, partes(20, -1L)).partes())
                .hasSize(20);
        assertThrows(DatosInvalidosException.class,
                () -> referencias.dividir(PRESUPUESTO_ID, null, -21L, partes(21, -1L)));
    }

    @Test
    void categoriaPropiaJuntoAUnaDivisionDa400() {
        assertThrows(DatosInvalidosException.class, () -> referencias.dividir(
                PRESUPUESTO_ID, 1L, -2000L, List.of(parte(null, -1000L), parte(null, -1000L))));
    }

    @Test
    void laSumaDistintaDelMontoDa422() {
        NegocioException error = assertThrows(ReglaNegocioException.class,
                () -> referencias.dividir(PRESUPUESTO_ID, null, -3000L,
                        List.of(parte(null, -1000L), parte(null, -1500L))));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
    }

    @Test
    void unDesbordamientoDeLaSumaSeTrataComoSumaIncorrecta() {
        assertThrows(ReglaNegocioException.class, () -> referencias.dividir(
                PRESUPUESTO_ID, null, 1L,
                List.of(parte(null, Long.MAX_VALUE), parte(null, Long.MAX_VALUE))));
    }

    @Test
    void unaCategoriaAjenaEnUnaParteDa404AntesQueCualquierOtraRegla() {
        assertThrows(RecursoNoEncontradoException.class, () -> referencias.dividir(
                PRESUPUESTO_ID, null, -1L, List.of(parte(99L, -1L))));
    }

    private static SubTransaccionRequest parte(Long categoriaId, long monto) {
        return new SubTransaccionRequest(categoriaId, monto, null);
    }

    private static List<SubTransaccionRequest> partes(int cantidad, long monto) {
        List<SubTransaccionRequest> partes = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            partes.add(parte(null, monto));
        }
        return partes;
    }

    @Test
    void exigirNoEsTransferenciaRechazaUnaPataConMensajeClaro() {
        com.presupuesto.transaccion.entity.Transaccion pata =
                com.presupuesto.transaccion.entity.Transaccion.builder().build();
        pata.enlazarCon(com.presupuesto.transaccion.entity.Transaccion.builder().build());

        NegocioException error = assertThrows(ReglaNegocioException.class,
                () -> referencias.exigirNoEsTransferencia(pata));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        assertThat(error.getMessage()).isEqualTo(
                "Es parte de una transferencia; usa /transferencias");
    }

    @Test
    void exigirNoEsTransferenciaAceptaUnaTransaccionNormal() {
        referencias.exigirNoEsTransferencia(
                com.presupuesto.transaccion.entity.Transaccion.builder().build());
    }

    // ---------- categoría de pago de tarjeta ----------

    private Categoria pagoDeTarjeta() {
        Categoria pago = Categoria.builder().id(3L)
                .cuentaTarjeta(Cuenta.builder().id(44L).build()).build();
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(3L, PRESUPUESTO_ID))
                .thenReturn(Optional.of(pago));
        return pago;
    }

    @Test
    void categoriaParaRegistrarAceptaNulaNormalYOcultaYRechazaLaDePagoCon422() {
        Categoria pago = pagoDeTarjeta();

        assertThat(referencias.categoriaParaRegistrar(null, PRESUPUESTO_ID)).isNull();
        assertThat(referencias.categoriaParaRegistrar(1L, PRESUPUESTO_ID)).isSameAs(comida);
        assertThat(referencias.categoriaParaRegistrar(2L, PRESUPUESTO_ID)).isSameAs(oculta);
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> referencias.categoriaParaRegistrar(3L, PRESUPUESTO_ID));
        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        assertThat(error.getMessage()).isEqualTo(TransaccionReferencias.MENSAJE_CATEGORIA_DE_PAGO);
        assertThat(referencias.categoria(3L, PRESUPUESTO_ID)).isSameAs(pago);
    }

    @Test
    void categoriaParaRegistrarSigueDando404ParaUnaAjena() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> referencias.categoriaParaRegistrar(99L, PRESUPUESTO_ID));
    }

    @Test
    void dividirRechazaLaCategoriaDePagoEnLaTransaccionYEnCualquierParte() {
        pagoDeTarjeta();

        assertThrows(ReglaNegocioException.class,
                () -> referencias.dividir(PRESUPUESTO_ID, 3L, -1000L, List.of()));
        assertThrows(ReglaNegocioException.class, () -> referencias.dividir(
                PRESUPUESTO_ID, null, -2500L, List.of(parte(1L, -1000L), parte(3L, -1500L))));
    }
}
