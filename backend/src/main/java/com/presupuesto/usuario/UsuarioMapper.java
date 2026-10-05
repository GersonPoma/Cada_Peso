package com.presupuesto.usuario;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
interface UsuarioMapper {

    @Mapping(target = "email", source = "usuario.email")
    @Mapping(target = "rol", source = "usuario.rol")
    UsuarioActualResponse aUsuarioActual(Perfil perfil);
}
