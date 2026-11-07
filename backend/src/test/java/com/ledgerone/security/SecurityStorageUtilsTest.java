package com.ledgerone.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SecurityStorageUtilsTest {
    @TempDir Path root;

    @Test void validBasenameResolvesInsideCanonicalDirectory() throws Exception {
        assertThat(SecurityStorageUtils.resolveUploadFile(root, "statement-2026.csv"))
                .isEqualTo(root.toRealPath().resolve("statement-2026.csv").toFile());
    }

    @Test void publicEntryPointUsesTheFixedUploadDirectory() throws Exception {
        Path expected = Path.of("/tmp/ledgerone_uploads").toFile().getCanonicalFile().toPath().resolve("statement.csv");
        assertThat(SecurityStorageUtils.resolveUploadFile("statement.csv")).isEqualTo(expected.toFile());
    }

    @Test void standardDoubleDotsAreRemovedFromBasenames() {
        assertThat(SecurityStorageUtils.resolveUploadFile(root, "statement..csv").getName()).isEqualTo("statementcsv");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ".", "..", "....", "../secret", "sub/../../secret", "/etc/passwd",
            "..\\secret", "C:\\secret", "%2e%2e%2fsecret", "%252e%252e%252fsecret", "bad\u0000.csv"})
    void invalidOrTraversalFileNamesAreRejected(String name) {
        assertThatThrownBy(() -> SecurityStorageUtils.resolveUploadFile(root, name)).isInstanceOf(SecurityException.class);
    }

    @Test void canonicalCheckRejectsASymlinkOutsideTheBase() throws Exception {
        Path base = Files.createDirectory(root.resolve("uploads"));
        Path outside = Files.writeString(root.resolve("outside.csv"), "not an upload");
        Files.createSymbolicLink(base.resolve("statement.csv"), outside);
        assertThatThrownBy(() -> SecurityStorageUtils.resolveUploadFile(base, "statement.csv"))
                .isInstanceOf(SecurityException.class).hasMessageContaining("escapes");
    }

    @Test void canonicalCheckRejectsSiblingWithTheSameDirectoryPrefix() throws Exception {
        Path base = Files.createDirectory(root.resolve("uploads"));
        Path sibling = Files.createDirectory(root.resolve("uploads-other"));
        Path outside = Files.writeString(sibling.resolve("statement.csv"), "not an upload");
        Files.createSymbolicLink(base.resolve("statement.csv"), outside);
        assertThatThrownBy(() -> SecurityStorageUtils.resolveUploadFile(base, "statement.csv"))
                .isInstanceOf(SecurityException.class);
    }
}
