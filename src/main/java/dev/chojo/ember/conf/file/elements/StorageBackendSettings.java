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

    /**
     * A detached copy of the given settings, every field of every backend included, so a change to
     * the original can be taken back by copying this one onto it.
     *
     * @param settings the settings to copy
     * @return the copy
     */
    public static StorageBackendSettings copyOf(StorageBackendSettings settings) {
        var copy = new StorageBackendSettings();
        copy.copyFrom(settings);
        return copy;
    }

    /**
     * Takes over every field of the given settings, for all four backends at once.
     *
     * @param source the settings to take over
     */
    public void copyFrom(StorageBackendSettings source) {
        type = source.type;
        local.root(source.local.root);
        smb.copyFrom(source.smb);
        sftp.copyFrom(source.sftp);
        s3.copyFrom(source.s3);
    }

    public StorageBackendType type() {
        return type;
    }

    public void type(StorageBackendType type) {
        this.type = type;
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

        public void root(String root) {
            this.root = root;
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

        void copyFrom(SmbSettings source) {
            host = source.host;
            port = source.port;
            share = source.share;
            domain = source.domain;
            username = source.username;
            password = source.password;
            passwordEnc = source.passwordEnc;
            basePath = source.basePath;
            seal = source.seal;
            dfs = source.dfs;
        }

        public String host() {
            return host;
        }

        public void host(String host) {
            this.host = host;
        }

        public int port() {
            return port;
        }

        public void port(int port) {
            this.port = port;
        }

        public String share() {
            return share;
        }

        public void share(String share) {
            this.share = share;
        }

        public String domain() {
            return domain;
        }

        public void domain(String domain) {
            this.domain = domain;
        }

        public String username() {
            return username;
        }

        public void username(String username) {
            this.username = username;
        }

        public String password() {
            return password;
        }

        public void password(String password) {
            this.password = password;
        }

        public EncryptedBlob passwordEnc() {
            return passwordEnc;
        }

        public void passwordEnc(EncryptedBlob passwordEnc) {
            this.passwordEnc = passwordEnc;
        }

        public String basePath() {
            return basePath;
        }

        public void basePath(String basePath) {
            this.basePath = basePath;
        }

        public boolean seal() {
            return seal;
        }

        public void seal(boolean seal) {
            this.seal = seal;
        }

        public boolean dfs() {
            return dfs;
        }

        public void dfs(boolean dfs) {
            this.dfs = dfs;
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

        void copyFrom(SftpSettings source) {
            host = source.host;
            port = source.port;
            username = source.username;
            password = source.password;
            passwordEnc = source.passwordEnc;
            privateKey = source.privateKey;
            privateKeyEnc = source.privateKeyEnc;
            knownHostsFingerprint = source.knownHostsFingerprint;
            basePath = source.basePath;
        }

        public String host() {
            return host;
        }

        public void host(String host) {
            this.host = host;
        }

        public int port() {
            return port;
        }

        public void port(int port) {
            this.port = port;
        }

        public String username() {
            return username;
        }

        public void username(String username) {
            this.username = username;
        }

        public String password() {
            return password;
        }

        public void password(String password) {
            this.password = password;
        }

        public EncryptedBlob passwordEnc() {
            return passwordEnc;
        }

        public void passwordEnc(EncryptedBlob passwordEnc) {
            this.passwordEnc = passwordEnc;
        }

        public String privateKey() {
            return privateKey;
        }

        public void privateKey(String privateKey) {
            this.privateKey = privateKey;
        }

        public EncryptedBlob privateKeyEnc() {
            return privateKeyEnc;
        }

        public void privateKeyEnc(EncryptedBlob privateKeyEnc) {
            this.privateKeyEnc = privateKeyEnc;
        }

        public String knownHostsFingerprint() {
            return knownHostsFingerprint;
        }

        public void knownHostsFingerprint(String knownHostsFingerprint) {
            this.knownHostsFingerprint = knownHostsFingerprint;
        }

        public String basePath() {
            return basePath;
        }

        public void basePath(String basePath) {
            this.basePath = basePath;
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

        void copyFrom(S3Settings source) {
            endpoint = source.endpoint;
            region = source.region;
            bucket = source.bucket;
            accessKey = source.accessKey;
            accessKeyEnc = source.accessKeyEnc;
            secretKey = source.secretKey;
            secretKeyEnc = source.secretKeyEnc;
            pathStyle = source.pathStyle;
            sseAlgorithm = source.sseAlgorithm;
            basePath = source.basePath;
        }

        public String endpoint() {
            return endpoint;
        }

        public void endpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String region() {
            return region;
        }

        public void region(String region) {
            this.region = region;
        }

        public String bucket() {
            return bucket;
        }

        public void bucket(String bucket) {
            this.bucket = bucket;
        }

        public String accessKey() {
            return accessKey;
        }

        public void accessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public EncryptedBlob accessKeyEnc() {
            return accessKeyEnc;
        }

        public void accessKeyEnc(EncryptedBlob accessKeyEnc) {
            this.accessKeyEnc = accessKeyEnc;
        }

        public String secretKey() {
            return secretKey;
        }

        public void secretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public EncryptedBlob secretKeyEnc() {
            return secretKeyEnc;
        }

        public void secretKeyEnc(EncryptedBlob secretKeyEnc) {
            this.secretKeyEnc = secretKeyEnc;
        }

        public boolean pathStyle() {
            return pathStyle;
        }

        public void pathStyle(boolean pathStyle) {
            this.pathStyle = pathStyle;
        }

        public String sseAlgorithm() {
            return sseAlgorithm;
        }

        public void sseAlgorithm(String sseAlgorithm) {
            this.sseAlgorithm = sseAlgorithm;
        }

        public String basePath() {
            return basePath;
        }

        public void basePath(String basePath) {
            this.basePath = basePath;
        }
    }
}
