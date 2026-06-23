/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;

/**
 * Instance-default storage backend selection plus per-backend connection settings. Only the
 * sub-block matching {@link #type()} is read; the rest are ignored.
 *
 * <p>Credential fields exist in two forms: a legacy plain-text variant (e.g. {@code accessKey})
 * that an operator may set by hand for a fresh deployment, and an encrypted variant (e.g.
 * {@code accessKeyEnc}) that the in-app instance-storage swap writes back when an
 * administrator changes the backend through the admin UI. {@code StorageBackendFactory}
 * prefers the encrypted form and falls back to the plain field when the encrypted slot is
 * empty so existing deployments keep working unchanged.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
public class StorageBackendSettings {
    private StorageBackendType type = StorageBackendType.LOCAL;
    private LocalSettings local = new LocalSettings();
    private SmbSettings smb = new SmbSettings();
    private SftpSettings sftp = new SftpSettings();
    private S3Settings s3 = new S3Settings();

    public StorageBackendType type() {
        return type;
    }

    public LocalSettings local() {
        return local;
    }

    public SmbSettings smb() {
        return smb;
    }

    public SftpSettings sftp() {
        return sftp;
    }

    public S3Settings s3() {
        return s3;
    }

    /**
     * Settings for the local-disk backend. Default root is {@code data/}.
     */
    @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
    public static class LocalSettings {
        private String root = "data";

        public String root() {
            return root;
        }
    }

    /**
     * Settings for the SMB3 backend. {@code password} carries plain-text from a hand-edited
     * config; {@code passwordEnc} carries the encrypted form written back by the admin UI.
     */
    @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
    public static class SmbSettings {
        private String host = "";
        private int port = 445;
        private String share = "";
        private String domain = "";
        private String username = "";
        private String password = "";
        private EncryptedBlob passwordEnc = null;
        private String basePath = "";
        private boolean seal = true;
        private boolean dfs = false;

        public String host() {
            return host;
        }

        public int port() {
            return port;
        }

        public String share() {
            return share;
        }

        public String domain() {
            return domain;
        }

        public String username() {
            return username;
        }

        public String password() {
            return password;
        }

        public EncryptedBlob passwordEnc() {
            return passwordEnc;
        }

        public String basePath() {
            return basePath;
        }

        public boolean seal() {
            return seal;
        }

        public boolean dfs() {
            return dfs;
        }
    }

    /**
     * Settings for the SFTP backend. Either {@code password} (or its {@code passwordEnc}
     * counterpart) or {@code privateKey} (or {@code privateKeyEnc}) is set.
     */
    @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
    public static class SftpSettings {
        private String host = "";
        private int port = 22;
        private String username = "";
        private String password = "";
        private EncryptedBlob passwordEnc = null;
        private String privateKey = "";
        private EncryptedBlob privateKeyEnc = null;
        private String knownHostsFingerprint = "";
        private String basePath = "";

        public String host() {
            return host;
        }

        public int port() {
            return port;
        }

        public String username() {
            return username;
        }

        public String password() {
            return password;
        }

        public EncryptedBlob passwordEnc() {
            return passwordEnc;
        }

        public String privateKey() {
            return privateKey;
        }

        public EncryptedBlob privateKeyEnc() {
            return privateKeyEnc;
        }

        public String knownHostsFingerprint() {
            return knownHostsFingerprint;
        }

        public String basePath() {
            return basePath;
        }
    }

    /**
     * Settings for the S3 backend. Works against AWS S3 and any S3-compatible endpoint.
     */
    @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
    public static class S3Settings {
        private String endpoint = "";
        private String region = "";
        private String bucket = "";
        private String accessKey = "";
        private EncryptedBlob accessKeyEnc = null;
        private String secretKey = "";
        private EncryptedBlob secretKeyEnc = null;
        private boolean pathStyle = false;
        private String sseAlgorithm = "";
        private String basePath = "";

        public String endpoint() {
            return endpoint;
        }

        public String region() {
            return region;
        }

        public String bucket() {
            return bucket;
        }

        public String accessKey() {
            return accessKey;
        }

        public EncryptedBlob accessKeyEnc() {
            return accessKeyEnc;
        }

        public String secretKey() {
            return secretKey;
        }

        public EncryptedBlob secretKeyEnc() {
            return secretKeyEnc;
        }

        public boolean pathStyle() {
            return pathStyle;
        }

        public String sseAlgorithm() {
            return sseAlgorithm;
        }

        public String basePath() {
            return basePath;
        }
    }
}
