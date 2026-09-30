/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.sftp;

import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendContract;
import dev.chojo.ember.feature.storage.backend.StorageContainers;
import org.junit.jupiter.api.Tag;

import java.util.Optional;

@Tag("storage")
class SftpStorageBackendContractTest extends StorageBackendContract {

    /** The settings the backend under test signs in with. */
    static SftpBackendConfig config() {
        var sftp = StorageContainers.sftp();
        return new SftpBackendConfig(
                sftp.getHost(),
                sftp.getMappedPort(22),
                StorageContainers.USER,
                Optional.of(StorageContainers.PASSWORD),
                Optional.empty(),
                "",
                StorageContainers.SFTP_BASE);
    }

    @Override
    protected StorageBackend openBackend() {
        return new SftpStorageBackend(config());
    }
}
