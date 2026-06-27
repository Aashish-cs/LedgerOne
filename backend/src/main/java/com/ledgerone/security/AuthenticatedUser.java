package com.ledgerone.security;

import com.ledgerone.entity.AccountStatus;
import com.ledgerone.entity.RoleName;
import com.ledgerone.entity.UserAccount;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AuthenticatedUser(
        UUID id,
        String email,
        String fullName,
        String password,
        AccountStatus status,
        Set<RoleName> roles,
        Collection<? extends GrantedAuthority> authorities)
        implements UserDetails {

    public static AuthenticatedUser from(UserAccount user) {
        Set<RoleName> roleNames = user.getRoles().stream().map(role -> role.getName()).collect(Collectors.toSet());
        Collection<GrantedAuthority> authorities = roleNames.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());
        return new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPasswordHash(),
                user.getStatus(),
                roleNames,
                authorities);
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != AccountStatus.FROZEN;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
