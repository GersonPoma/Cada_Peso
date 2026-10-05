package com.presupuesto.usuario.mapper;

import com.presupuesto.usuario.dto.UsuarioActualResponse;
import com.presupuesto.usuario.entity.Perfil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(target = "email", source = "usuario.email")
    @Mapping(target = "rol", source = "usuario.rol")
    UsuarioActualResponse aUsuarioActual(Perfil perfil);
}
