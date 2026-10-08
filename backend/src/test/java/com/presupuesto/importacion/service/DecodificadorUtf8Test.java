package com.presupuesto.importacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.comun.excepcion.DatosInvalidosException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DecodificadorUtf8Test {

    @Test
    void decodificaUtf8ConAcentos() {
        byte[] bytes = "Café ñandú".getBytes(StandardCharsets.UTF_8);

        assertThat(DecodificadorUtf8.decodificar(bytes)).isEqualTo("Café ñandú");
    }

    @Test
    void descartaElBom() {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.writeBytes(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        salida.writeBytes("fecha,monto".getBytes(StandardCharsets.UTF_8));

        assertThat(DecodificadorUtf8.decodificar(salida.toByteArray())).isEqualTo("fecha,monto");
    }

    @Test
    void iso88591ConAcentosEsUn400QueMencionaUtf8() {
        byte[] bytes = "Café".getBytes(StandardCharsets.ISO_8859_1);

        DatosInvalidosException e = assertThrows(
                DatosInvalidosException.class, () -> DecodificadorUtf8.decodificar(bytes));

        assertThat(e.getMessage()).contains("UTF-8");
    }

    @Test
    void utf16ConBomEsUn400() {
        byte[] bytes = "fecha".getBytes(StandardCharsets.UTF_16);

        assertThrows(DatosInvalidosException.class, () -> DecodificadorUtf8.decodificar(bytes));
    }

    @Test
    void utf16SinBomEsUn400PorLosNul() {
        byte[] bytes = "fecha".getBytes(StandardCharsets.UTF_16LE);

        assertThrows(DatosInvalidosException.class, () -> DecodificadorUtf8.decodificar(bytes));
    }

    @Test
    void unaSecuenciaUtf8Truncada() {
        assertThrows(DatosInvalidosException.class,
                () -> DecodificadorUtf8.decodificar(new byte[] {'a', (byte) 0xC3}));
    }

    @Test
    void unArchivoAsciiEsValidoComoUtf8() {
        assertThat(DecodificadorUtf8.decodificar("a,b".getBytes(StandardCharsets.ISO_8859_1)))
                .isEqualTo("a,b");
    }
}
