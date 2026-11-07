package com.ledgerone.security;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public final class SecurityStorageUtils {
    private static final Path UPLOAD_DIRECTORY = Path.of("/tmp/ledgerone_uploads");

    private SecurityStorageUtils() {}

    public static File resolveUploadFile(String fileName) {
        return resolveUploadFile(UPLOAD_DIRECTORY, fileName);
    }

    static File resolveUploadFile(Path baseDirectory, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new SecurityException("An upload file name is required");
        }
        String sanitized = fileName.replace("..", "");
        // Only a basename is accepted: no directories, encodings, drive names or control characters.
        if (!sanitized.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,254}")) {
            throw new SecurityException("Invalid upload file name");
        }
        try {
            Path base = Path.of(baseDirectory.toFile().getCanonicalPath());
            Path target = Path.of(base.resolve(sanitized).toFile().getCanonicalPath());
            if (target.equals(base) || !target.startsWith(base)) {
                throw new SecurityException("Upload path escapes the storage directory");
            }
            return target.toFile();
        } catch (IOException exception) {
            throw new SecurityException("Cannot validate the upload path", exception);
        }
    }
}
