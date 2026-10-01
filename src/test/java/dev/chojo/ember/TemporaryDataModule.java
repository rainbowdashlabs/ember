/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.discovery.service.DiscoveryKeyService;
import dev.chojo.ember.feature.maps.service.MapTileCacheService;
import dev.chojo.ember.feature.maps.service.MapsConfigService;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.EncryptionKeyFile;

import java.nio.file.Path;

/**
 * The application's module with everything it keeps under {@code data/} moved into a directory of the
 * test's choosing, so that a test building the real application leaves the working directory as it
 * found it.
 *
 * <p>Covered are the services that write there as soon as they are built: the discovery keypair, the
 * local storage backend (where the logo fragments land), the map tile cache and the generated
 * encryption key. Each keeps the layout it has in production, only below the given directory.
 */
public final class TemporaryDataModule extends AbstractModule {
    private final Path root;

    private TemporaryDataModule(Path root) {
        this.root = root;
    }

    /**
     * The application's module, keeping its data below the given directory.
     *
     * @param conf the configuration the application is built with
     * @param root the directory standing in for {@code data/}
     * @return the module to build the injector from
     */
    public static Module application(Conf conf, Path root) {
        return Modules.override(new EmberModule(conf)).with(new TemporaryDataModule(root));
    }

    @Provides
    @Singleton
    DiscoveryKeyService discoveryKeys() {
        return new DiscoveryKeyService(root.resolve("discovery"));
    }

    @Provides
    @Singleton
    LocalStorageBackend localStorage() {
        return new LocalStorageBackend(root);
    }

    @Provides
    @Singleton
    MapTileCacheService mapTileCache(MapsConfigService configService) {
        return new MapTileCacheService(configService, root.resolve("maps").resolve("tile-cache"), null);
    }

    @Provides
    @Singleton
    CredentialCipher credentialCipher(Storage storage) {
        var keyFile = new EncryptionKeyFile(root.resolve("secrets").resolve("encryption.key"));
        return new CredentialCipher(keyFile.keyFor(storage.credentialEncryptionKey()));
    }
}
