package com.ledgerone.mapper;

import com.ledgerone.dto.AdminDtos;
import com.ledgerone.dto.AuthDtos;
import com.ledgerone.entity.RoleName;
import com.ledgerone.entity.UserAccount;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "roles", expression = "java(toRoleNames(user))")
    AuthDtos.UserPrincipalResponse toPrincipal(UserAccount user);

    @Mapping(target = "roles", expression = "java(toRoleNames(user))")
    AdminDtos.UserAdminResponse toAdmin(UserAccount user);

    default Set<RoleName> toRoleNames(UserAccount user) {
        return user.getRoles().stream().map(role -> role.getName()).collect(Collectors.toSet());
    }
}
