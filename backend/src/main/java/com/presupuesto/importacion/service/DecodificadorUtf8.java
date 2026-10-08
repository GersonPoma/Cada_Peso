package com.presupuesto.importacion.service;

import com.presupuesto.comun.excepcion.DatosInvalidosException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Decodifica el archivo como UTF-8 estricto, con o sin BOM: un byte inválido es un 400 en lugar
 * de reemplazarse en silencio por {@code U+FFFD}.
 */
final class DecodificadorUtf8 {

    static final String MENSAJE_NO_UTF8 = "El archivo debe estar codificado en UTF-8";

    private DecodificadorUtf8() {}

    static String decodificar(byte[] bytes) {
        int inicio = tieneBom(bytes) ? 3 : 0;
        try {
            String texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, inicio, bytes.length - inicio))
                    .toString();
            // UTF-16 sin BOM decodifica como UTF-8 válido pero lleno de NUL.
            if (texto.indexOf('\u0000') >= 0) {
                throw new DatosInvalidosException(MENSAJE_NO_UTF8);
            }
            return texto;
        } catch (CharacterCodingException e) {
            throw new DatosInvalidosException(MENSAJE_NO_UTF8);
        }
    }

    private static boolean tieneBom(byte[] bytes) {
        return bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF;
    }
}
