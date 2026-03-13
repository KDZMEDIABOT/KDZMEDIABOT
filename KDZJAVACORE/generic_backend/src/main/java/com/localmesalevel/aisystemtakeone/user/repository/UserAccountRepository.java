package com.localmesalevel.aisystemtakeone.user.repository;

import com.localmesalevel.aisystemtakeone.user.model.UserAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);
    Page<UserAccount> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
}

