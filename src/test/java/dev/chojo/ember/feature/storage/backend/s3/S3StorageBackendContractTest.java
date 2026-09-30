/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.s3;

import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendContract;
import dev.chojo.ember.feature.storage.backend.StorageContainers;
import org.junit.jupiter.api.Tag;

import java.util.Optional;

@Tag("storage")
class S3StorageBackendContractTest extends StorageBackendContract {

    @Override
    protected StorageBackend openBackend() {
        StorageContainers.s3();
        return new S3StorageBackend(new S3BackendConfig(
                StorageContainers.s3Endpoint(),
                StorageContainers.REGION,
                StorageContainers.BUCKET,
                StorageContainers.USER,
                StorageContainers.PASSWORD,
                true,
                Optional.empty(),
                ""));
    }
}
