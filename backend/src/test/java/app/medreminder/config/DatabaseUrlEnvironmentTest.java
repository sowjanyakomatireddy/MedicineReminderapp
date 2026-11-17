package app.medreminder.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseUrlEnvironmentTest {

    @Test
    void splitsAHostedDatabaseLine() {
        Map<String, Object> p = DatabaseUrlEnvironment.parse(
                "postgresql://neondb_owner:p%40ss@ep-cool-1.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require");
        assertThat(p).containsEntry("spring.datasource.url",
                        "jdbc:postgresql://ep-cool-1.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require")
                .containsEntry("spring.datasource.username", "neondb_owner")
                .containsEntry("spring.datasource.password", "p@ss");
    }

    @Test
    void forgivesACopiedEnvLine() {
        assertThat(DatabaseUrlEnvironment.clean(" DATABASE_URL=\"postgresql://u:x@h/db?sslmode=require\" "))
                .isEqualTo("postgresql://u:x@h/db?sslmode=require");
        assertThat(DatabaseUrlEnvironment.clean("'postgresql://u:x@h/db'")).isEqualTo("postgresql://u:x@h/db");
    }

    @Test
    void keepsThePort() {
        assertThat(DatabaseUrlEnvironment.parse("postgres://u:x@db.example:6543/app"))
                .containsEntry("spring.datasource.url", "jdbc:postgresql://db.example:6543/app");
    }

    @Test
    void refusesSomethingElse() {
        assertThatThrownBy(() -> DatabaseUrlEnvironment.parse("mysql://u:x@h/db")).isInstanceOf(IllegalStateException.class);
    }
}
