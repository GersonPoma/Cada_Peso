package com.presupuesto.importacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.presupuesto.comun.excepcion.DatosInvalidosException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

class LectorCsvTest {

    static Stream<Arguments> casos() {
        return Stream.of(
                Arguments.of("a,b,c", ',', List.of(List.of("a", "b", "c"))),
                Arguments.of("a,b\nc,d", ',', List.of(List.of("a", "b"), List.of("c", "d"))),
                Arguments.of("a,b\r\nc,d\r\n", ',', List.of(List.of("a", "b"), List.of("c", "d"))),
                Arguments.of("a,b\rc,d", ',', List.of(List.of("a", "b"), List.of("c", "d"))),
                Arguments.of("a;b;c", ';', List.of(List.of("a", "b", "c"))),
                Arguments.of("a\tb", '\t', List.of(List.of("a", "b"))),
                Arguments.of("\"a,b\",c", ',', List.of(List.of("a,b", "c"))),
                Arguments.of("\"di \"\"hola\"\"\",x", ',', List.of(List.of("di \"hola\"", "x"))),
                Arguments.of("\"uno\ndos\",x", ',', List.of(List.of("uno\ndos", "x"))),
                Arguments.of("\"uno\r\ndos\",x", ',', List.of(List.of("uno\r\ndos", "x"))),
                Arguments.of("a,,c", ',', List.of(List.of("a", "", "c"))),
                Arguments.of("a,b,", ',', List.of(List.of("a", "b", ""))),
                Arguments.of(",", ',', List.of(List.of("", ""))),
                Arguments.of("\"\",x", ',', List.of(List.of("", "x"))),
                Arguments.of("a,b\n\n\nc,d\n", ',', List.of(List.of("a", "b"), List.of("c", "d"))),
                Arguments.of("", ',', List.of()),
                Arguments.of("\n\r\n", ',', List.of()),
                Arguments.of("ab\"cd,e", ',', List.of(List.of("ab\"cd", "e"))),
                Arguments.of("\"ab\"cd,e", ',', List.of(List.of("abcd", "e"))));
    }

    @ParameterizedTest
    @MethodSource("casos")
    void leeLosRegistros(String texto, char separador, List<List<String>> esperado) {
        assertThat(LectorCsv.leer(texto, separador, 100)).isEqualTo(esperado);
    }

    @Test
    void unaComillaSinCerrarEsUn400() {
        assertThrows(DatosInvalidosException.class, () -> LectorCsv.leer("a,\"b\nc", ',', 100));
        assertThrows(DatosInvalidosException.class, () -> LectorCsv.leer("\"", ',', 100));
    }

    @Test
    void cortaAlSuperarElMaximoDeRegistros() {
        assertThat(LectorCsv.leer("1\n2\n3", ',', 3)).hasSize(3);
        assertThrows(DatosInvalidosException.class, () -> LectorCsv.leer("1\n2\n3\n4", ',', 3));
    }

    @Test
    void laLineaVaciaNoCuentaParaElMaximo() {
        assertThat(LectorCsv.leer("1\n\n2\n\n", ',', 2)).hasSize(2);
    }
}
