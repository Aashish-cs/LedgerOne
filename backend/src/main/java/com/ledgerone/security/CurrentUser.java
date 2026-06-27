package com.ledgerone.security;

import com.ledgerone.entity.UserAccount;
import com.ledgerone.exception.ResourceNotFoundException;
import com.ledgerone.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUser {
    private final UserAccountRepository userRepository;

    public AuthenticatedUser principal() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        throw new ResourceNotFoundException("Authenticated user not found");
    }

    public UserAccount entity() {
        return userRepository
                .findById(principal().id())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }
}
