/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two backends built apart from each other name the same destination exactly when they reach the same
 * files: a new secret, user name, region or addressing style leaves the place where it is, while another
 * host, bucket, share or path moves it.
 */
class StorageBackendDestinationTest {
    private static final CredentialCipher CIPHER =
            new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));

    @TempDir
    Path root;

    private StorageBackendFactory factory;
    private final List<StorageBackend> built = new ArrayList<>();

    @BeforeEach
    void setUp() {
        factory = new StorageBackendFactory(new Storage(), new LocalStorageBackend(root), CIPHER);
    }

    @AfterEach
    void tearDown() {
        built.forEach(StorageBackend::close);
        factory.clients().close();
    }

    private StorageBackend build(StationStorageBackendConfig config) {
        var backend = factory.buildForStation(config);
        built.add(backend);
        return backend;
    }

    private static StationStorageBackendConfig s3(String region, boolean pathStyle, String bucket, String secret) {
        return new StationStorageBackendConfig.S3Variant(
                "https://s3.example.invalid",
                region,
                bucket,
                pathStyle,
                Optional.empty(),
                "/files/",
                CIPHER.encrypt("{\"accessKey\":\"ak\",\"secretKey\":\"" + secret + "\"}"));
    }

    private static StationStorageBackendConfig smb(String share, String domain, String user) {
        return new StationStorageBackendConfig.SmbVariant(
                "nas.example.invalid",
                445,
                share,
                domain,
                "ember",
                true,
                false,
                CIPHER.encrypt("{\"username\":\"" + user + "\",\"password\":\"pw\"}"));
    }

    private static StationStorageBackendConfig sftp(String basePath, String user) {
        return new StationStorageBackendConfig.SftpVariant(
                "sftp.example.invalid",
                22,
                user,
                "",
                basePath,
                CIPHER.encrypt("{\"username\":\"" + user + "\",\"password\":\"pw\",\"privateKey\":\"\"}"));
    }

    @Test
    void s3BackendsMeetInTheSameBucketWhateverTheyUseToSignIn() {
        var current = build(s3("eu-central-1", true, "station", "old"));

        assertTrue(current.sharesDestinationWith(build(s3("us-east-1", false, "station", "new"))));
        assertFalse(current.sharesDestinationWith(build(s3("eu-central-1", true, "elsewhere", "old"))));
    }

    @Test
    void smbBackendsMeetOnTheSameShareWhateverTheyUseToSignIn() {
        var current = build(smb("archive", "WORK", "alice"));

        assertTrue(current.sharesDestinationWith(build(smb("archive", "HOME", "bob"))));
        assertFalse(current.sharesDestinationWith(build(smb("other", "WORK", "alice"))));
    }

    @Test
    void sftpBackendsMeetUnderTheSamePathWhateverTheyUseToSignIn() {
        var current = build(sftp("/srv/ember", "alice"));

        assertTrue(current.sharesDestinationWith(build(sftp("/srv/ember/", "bob"))));
        assertFalse(current.sharesDestinationWith(build(sftp("/srv/other", "alice"))));
    }

    /**
     * A stored configuration names exactly the place the backend built from it reports, so an association
     * deciding between new credentials and a new version agrees with the move that follows.
     */
    @Test
    void aConfigurationNamesThePlaceItsBackendReports() {
        for (var config : List.of(
                s3("eu-central-1", true, "station", "old"),
                smb("archive", "WORK", "alice"),
                sftp("/srv/ember/", "alice"))) {
            assertEquals(build(config).destination(), config.destinationKey());
        }
        assertEquals(
                s3("eu-central-1", true, "station", "old").destinationKey(),
                s3("us-east-1", false, "station", "new").destinationKey());
        assertEquals(
                smb("archive", "WORK", "alice").destinationKey(),
                smb("archive", "HOME", "bob").destinationKey());
    }

    @Test
    void localBackendsMeetOnTheSameDirectory() {
        var current = new LocalStorageBackend(root);

        assertTrue(current.sharesDestinationWith(new LocalStorageBackend(root.resolve("sub/.."))));
        assertFalse(current.sharesDestinationWith(new LocalStorageBackend(root.resolve("sub"))));
        assertFalse(current.sharesDestinationWith(build(sftp(root.toString(), "alice"))));
    }
}
