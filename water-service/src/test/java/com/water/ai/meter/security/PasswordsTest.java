package com.water.ai.meter.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordsTest {
    @Test void newPasswordsAreSaltedAndOnlyTheCorrectPasswordMatches() {
        String first=Passwords.hash("admin123"), second=Passwords.hash("admin123");
        assertThat(first).startsWith("$2").isNotEqualTo(second);
        assertThat(Passwords.matches("admin123",first)).isTrue();
        assertThat(Passwords.matches("wrong123",first)).isFalse();
        assertThat(Passwords.matches("admin123","different-existing-password")).isFalse();
    }
    @Test void legacyPasswordsCanUpgradeButMalformedHashesCannotBypassCheck() {
        assertThat(Passwords.matches("pass123","pass123")).isTrue();
        assertThat(Passwords.matches("pass123","$2a$broken")).isFalse();
        assertThat(Passwords.matches(null,null)).isFalse();
        assertThatThrownBy(()->Passwords.hash("short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->Passwords.hash("a".repeat(73))).isInstanceOf(IllegalArgumentException.class);
    }
}
