package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.AuthStore;
import com.example.smartcanteen.security.PasswordHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Creates a local admin only when an operator explicitly supplies a password. */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private final AuthStore store;
    private final PasswordHasher passwords;
    private final String password;
    private final String username;

    public BootstrapAdminRunner(
            AuthStore store,
            PasswordHasher passwords,
            @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password,
            @Value("${BOOTSTRAP_ADMIN_USERNAME:admin}") String username) {
        this.store = store;
        this.passwords = passwords;
        this.password = password;
        this.username = username;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            return;
        }
        store.ensureBootstrapAdmin(username, passwords.hash(password), "系统管理员");
    }
}
