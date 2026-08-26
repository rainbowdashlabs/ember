/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EnvVarGroup from './EnvVarGroup.vue'

const {t} = useI18n()

const groups = [
  {
    title: t('helpCenter.basics.configuration.envGroupDb'),
    defaultOpen: true,
    vars: [
      {name: 'DB_HOST', default: 'localhost', configKey: 'database.host', desc: t('helpCenter.basics.configuration.env.dbHost')},
      {name: 'DB_PORT', default: '5432', configKey: 'database.port', desc: t('helpCenter.basics.configuration.env.dbPort')},
      {name: 'DB_USER', default: 'ember', configKey: 'database.user', desc: t('helpCenter.basics.configuration.env.dbUser')},
      {name: 'DB_PASSWORD', default: 'ember', configKey: 'database.password', desc: t('helpCenter.basics.configuration.env.dbPassword')},
      {name: 'DB_DATABASE', default: 'ember', configKey: 'database.database', desc: t('helpCenter.basics.configuration.env.dbDatabase')},
      {name: 'DB_SCHEMA', default: 'ember', configKey: 'database.schema', desc: t('helpCenter.basics.configuration.env.dbSchema')},
      {name: 'DB_POOLSIZE', default: '5', configKey: 'database.poolSize', desc: t('helpCenter.basics.configuration.env.dbPoolSize')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupApi'),
    defaultOpen: true,
    vars: [
      {name: 'API_HOST', default: '0.0.0.0', configKey: 'api.host', desc: t('helpCenter.basics.configuration.env.apiHost')},
      {name: 'API_PORT', default: '8080', configKey: 'api.port', desc: t('helpCenter.basics.configuration.env.apiPort')},
      {name: 'API_BASEURL', default: 'http://localhost:5173', configKey: 'api.baseUrl', desc: t('helpCenter.basics.configuration.env.apiBaseUrl')},
      {name: 'API_DEMOURL', default: 'https://demo.ember-panel.de', configKey: 'api.demoUrl', desc: t('helpCenter.basics.configuration.env.apiDemoUrl')},
      {name: 'API_ALLOWEDORIGINS', default: '-', configKey: 'api.allowedOrigins', desc: t('helpCenter.basics.configuration.env.apiAllowedOrigins')},
      {name: 'API_MAXIMAGESIZEBYTES', default: '5242880', configKey: 'api.maxImageSizeBytes', desc: t('helpCenter.basics.configuration.env.apiMaxImageSize')},
      {name: 'API_PRIVACYPOLICYDIR', default: 'data/documents/privacy', configKey: 'api.privacyPolicyDir', desc: t('helpCenter.basics.configuration.env.apiPrivacyDir')},
      {name: 'API_CONSENTDIR', default: 'data/documents/consent', configKey: 'api.consentDir', desc: t('helpCenter.basics.configuration.env.apiConsentDir')},
      {name: 'API_TOSDIR', default: 'data/documents/tos', configKey: 'api.tosDir', desc: t('helpCenter.basics.configuration.env.apiTosDir')},
      {name: 'API_IMPRINTDIR', default: 'data/documents/imprint', configKey: 'api.imprintDir', desc: t('helpCenter.basics.configuration.env.apiImprintDir')},
      {name: 'API_HTTPGZIPENABLED', default: 'true', configKey: 'api.httpGzipEnabled', desc: t('helpCenter.basics.configuration.env.apiHttpGzipEnabled')},
      {name: 'API_HTTPGZIPLEVEL', default: '6', configKey: 'api.httpGzipLevel', desc: t('helpCenter.basics.configuration.env.apiHttpGzipLevel')},
      {name: 'API_HTTPGZIPMINSIZEBYTES', default: '1024', configKey: 'api.httpGzipMinSizeBytes', desc: t('helpCenter.basics.configuration.env.apiHttpGzipMinSize')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupMailing'),
    defaultOpen: true,
    vars: [
      {name: 'MAILING_PROVIDER', default: 'SMTP', configKey: 'mailing.provider', desc: t('helpCenter.basics.configuration.env.mailingProvider')},
      {name: 'MAILING_SMTP', default: '-', configKey: 'mailing.smtp', desc: t('helpCenter.basics.configuration.env.mailingSmtp')},
      {name: 'MAILING_USER', default: '-', configKey: 'mailing.user', desc: t('helpCenter.basics.configuration.env.mailingUser')},
      {name: 'MAILING_PASSWORD', default: '-', configKey: 'mailing.password', desc: t('helpCenter.basics.configuration.env.mailingPassword')},
      {name: 'MAILING_APIKEY', default: '-', configKey: 'mailing.apiKey', desc: t('helpCenter.basics.configuration.env.mailingApiKey')},
      {name: 'MAILING_SENDERADDRESS', default: '-', configKey: 'mailing.senderAddress', desc: t('helpCenter.basics.configuration.env.mailingSenderAddress')},
      {name: 'MAILING_SENDERNAME', default: 'Ember', configKey: 'mailing.senderName', desc: t('helpCenter.basics.configuration.env.mailingSenderName')},
      {name: 'MAILING_DAILYSENDLIMIT', default: '200', configKey: 'mailing.dailySendLimit', desc: t('helpCenter.basics.configuration.env.mailingDailyLimit')},
      {name: 'MAILING_NOTIFICATIONDIGESTINTERVALMINUTES', default: '60', configKey: 'mailing.notificationDigestIntervalMinutes', desc: t('helpCenter.basics.configuration.env.mailingDigestInterval')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupMailSmtp'),
    note: t('helpCenter.basics.configuration.envGroupMailSmtpNote'),
    defaultOpen: false,
    vars: [
      {default: '-', configKey: 'mailing.smtp.host', desc: t('helpCenter.basics.configuration.env.mailingSmtpHost')},
      {default: '665', configKey: 'mailing.smtp.port', desc: t('helpCenter.basics.configuration.env.mailingSmtpPort')},
      {default: 'false', configKey: 'mailing.smtp.ssl', desc: t('helpCenter.basics.configuration.env.mailingSmtpSsl')},
      {default: '{}', configKey: 'mailing.properties', desc: t('helpCenter.basics.configuration.env.mailingProperties')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupAuth'),
    defaultOpen: true,
    vars: [
      {name: 'AUTH_SESSIONMINUTES', default: '43200', configKey: 'auth.sessionMinutes', desc: t('helpCenter.basics.configuration.env.authSessionMinutes')},
      {name: 'AUTH_VERIFYTOKENHOURS', default: '24', configKey: 'auth.verifyTokenHours', desc: t('helpCenter.basics.configuration.env.authVerifyTokenHours')},
      {name: 'AUTH_PASSWORDTOKENHOURS', default: '72', configKey: 'auth.passwordTokenHours', desc: t('helpCenter.basics.configuration.env.authPasswordTokenHours')},
      {name: 'AUTH_TOKENBYTES', default: '32', configKey: 'auth.tokenBytes', desc: t('helpCenter.basics.configuration.env.authTokenBytes')},
      {name: 'AUTH_TOKENPEPPER', default: '-', configKey: 'auth.tokenPepper', desc: t('helpCenter.basics.configuration.env.authTokenPepper')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupHibp'),
    note: t('helpCenter.basics.configuration.envGroupHibpNote'),
    defaultOpen: false,
    vars: [
      {default: 'true', configKey: 'auth.hibp.enabled', desc: t('helpCenter.basics.configuration.env.hibpEnabled')},
      {default: '30', configKey: 'auth.hibp.staleAfterDays', desc: t('helpCenter.basics.configuration.env.hibpStaleAfterDays')},
      {default: 'https://api.pwnedpasswords.com/range/', configKey: 'auth.hibp.endpoint', desc: t('helpCenter.basics.configuration.env.hibpEndpoint')},
      {default: '5', configKey: 'auth.hibp.timeoutSeconds', desc: t('helpCenter.basics.configuration.env.hibpTimeout')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupTwoFactor'),
    note: t('helpCenter.basics.configuration.envGroupTwoFactorNote'),
    defaultOpen: false,
    vars: [
      {default: 'true', configKey: 'auth.twoFactor.enabled', desc: t('helpCenter.basics.configuration.env.twoFactorEnabled')},
      {default: '-', configKey: 'auth.twoFactor.secretKey', desc: t('helpCenter.basics.configuration.env.twoFactorSecretKey')},
      {default: '300', configKey: 'auth.twoFactor.stepUpFreshnessSeconds', desc: t('helpCenter.basics.configuration.env.twoFactorStepUpFreshness')},
      {default: '30', configKey: 'auth.twoFactor.trustedDeviceMaxDays', desc: t('helpCenter.basics.configuration.env.twoFactorTrustedDeviceMaxDays')},
      {default: '7', configKey: 'auth.twoFactor.enrollmentGraceDays', desc: t('helpCenter.basics.configuration.env.twoFactorEnrollmentGraceDays')},
      {default: '6', configKey: 'auth.twoFactor.totp.digits', desc: t('helpCenter.basics.configuration.env.totpDigits')},
      {default: '30', configKey: 'auth.twoFactor.totp.periodSeconds', desc: t('helpCenter.basics.configuration.env.totpPeriod')},
      {default: 'SHA1', configKey: 'auth.twoFactor.totp.algorithm', desc: t('helpCenter.basics.configuration.env.totpAlgorithm')},
      {default: '1', configKey: 'auth.twoFactor.totp.driftWindow', desc: t('helpCenter.basics.configuration.env.totpDrift')},
      {default: 'Ember', configKey: 'auth.twoFactor.totp.issuer', desc: t('helpCenter.basics.configuration.env.totpIssuer')},
      {default: '10', configKey: 'auth.twoFactor.backupCodes.count', desc: t('helpCenter.basics.configuration.env.backupCodesCount')},
      {default: '-', configKey: 'auth.twoFactor.webauthn.rpId', desc: t('helpCenter.basics.configuration.env.webauthnRpId')},
      {default: '-', configKey: 'auth.twoFactor.webauthn.rpName', desc: t('helpCenter.basics.configuration.env.webauthnRpName')},
      {default: 'none', configKey: 'auth.twoFactor.webauthn.attestation', desc: t('helpCenter.basics.configuration.env.webauthnAttestation')},
      {default: '60', configKey: 'auth.twoFactor.webauthn.timeoutSeconds', desc: t('helpCenter.basics.configuration.env.webauthnTimeout')},
      {default: 'false', configKey: 'auth.twoFactor.webauthn.requireResidentKey', desc: t('helpCenter.basics.configuration.env.webauthnRequireResidentKey')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupStorage'),
    note: t('helpCenter.basics.configuration.envGroupStorageNote'),
    defaultOpen: true,
    vars: [
      {name: 'STORAGE_DEFAULTTOTAL', default: '5G', configKey: 'storage.defaultTotal', desc: t('helpCenter.basics.configuration.env.storageDefaultTotal')},
      {name: 'STORAGE_DEFAULTKB', default: '4G', configKey: 'storage.defaultKb', desc: t('helpCenter.basics.configuration.env.storageDefaultKb')},
      {name: 'STORAGE_DEFAULTBOARD', default: '3G', configKey: 'storage.defaultBoard', desc: t('helpCenter.basics.configuration.env.storageDefaultBoard')},
      {name: 'STORAGE_DEFAULTIMAGES', default: '1G', configKey: 'storage.defaultImages', desc: t('helpCenter.basics.configuration.env.storageDefaultImages')},
      {name: 'STORAGE_DEFAULTPAGES', default: '1G', configKey: 'storage.defaultPages', desc: t('helpCenter.basics.configuration.env.storageDefaultPages')},
      {name: 'STORAGE_DEFAULTPERFILE', default: '50M', configKey: 'storage.defaultPerFile', desc: t('helpCenter.basics.configuration.env.storageDefaultPerFile')},
      {name: 'STORAGE_DEFAULTPERIMAGE', default: '5M', configKey: 'storage.defaultPerImage', desc: t('helpCenter.basics.configuration.env.storageDefaultPerImage')},
      {name: 'STORAGE_WARNINGTHRESHOLDPERCENT', default: '80', configKey: 'storage.warningThresholdPercent', desc: t('helpCenter.basics.configuration.env.storageWarningThreshold')},
      {name: 'STORAGE_RECONCILIATIONINTERVALHOURS', default: '24', configKey: 'storage.reconciliationIntervalHours', desc: t('helpCenter.basics.configuration.env.storageReconciliationInterval')},
      {name: 'STORAGE_CREDENTIALENCRYPTIONKEY', default: '-', configKey: 'storage.credentialEncryptionKey', desc: t('helpCenter.basics.configuration.env.storageCredentialKey')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupStorageCompress'),
    note: t('helpCenter.basics.configuration.envGroupStorageCompressNote'),
    defaultOpen: false,
    vars: [
      {name: 'STORAGE_COMPRESSPRESENTATIONS', default: 'true', configKey: 'storage.compressPresentations', desc: t('helpCenter.basics.configuration.env.storageCompressPresentations')},
      {name: 'STORAGE_COMPRESSOFFICEDOCS', default: 'true', configKey: 'storage.compressOfficeDocs', desc: t('helpCenter.basics.configuration.env.storageCompressOfficeDocs')},
      {name: 'STORAGE_COMPRESSPDFS', default: 'true', configKey: 'storage.compressPdfs', desc: t('helpCenter.basics.configuration.env.storageCompressPdfs')},
      {name: 'STORAGE_COMPRESSTEXTFILES', default: 'true', configKey: 'storage.compressTextFiles', desc: t('helpCenter.basics.configuration.env.storageCompressTextFiles')},
      {name: 'STORAGE_COMPRESSTHRESHOLD', default: '10M', configKey: 'storage.compressThreshold', desc: t('helpCenter.basics.configuration.env.storageCompressThreshold')},
      {name: 'STORAGE_IMAGEVARIANTSENABLED', default: 'true', configKey: 'storage.imageVariantsEnabled', desc: t('helpCenter.basics.configuration.env.storageImageVariantsEnabled')},
      {name: 'STORAGE_IMAGEVARIANTSWIDTHS', default: '128,256,512,1024,2048', configKey: 'storage.imageVariantsWidths', desc: t('helpCenter.basics.configuration.env.storageImageVariantsWidths')},
      {name: 'STORAGE_IMAGEVARIANTSWEBP', default: 'true', configKey: 'storage.imageVariantsWebp', desc: t('helpCenter.basics.configuration.env.storageImageVariantsWebp')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupStorageBackend'),
    note: t('helpCenter.basics.configuration.envGroupStorageBackendNote'),
    defaultOpen: false,
    vars: [
      {default: 'LOCAL', configKey: 'storage.backend.type', desc: t('helpCenter.basics.configuration.env.storageBackendType')},
      {default: 'data', configKey: 'storage.backend.local.root', desc: t('helpCenter.basics.configuration.env.storageLocalRoot')},
      {default: '-', configKey: 'storage.backend.s3.endpoint', desc: t('helpCenter.basics.configuration.env.storageS3Endpoint')},
      {default: '-', configKey: 'storage.backend.s3.region', desc: t('helpCenter.basics.configuration.env.storageS3Region')},
      {default: '-', configKey: 'storage.backend.s3.bucket', desc: t('helpCenter.basics.configuration.env.storageS3Bucket')},
      {default: '-', configKey: 'storage.backend.s3.accessKey', desc: t('helpCenter.basics.configuration.env.storageS3AccessKey')},
      {default: '-', configKey: 'storage.backend.s3.secretKey', desc: t('helpCenter.basics.configuration.env.storageS3SecretKey')},
      {default: 'false', configKey: 'storage.backend.s3.pathStyle', desc: t('helpCenter.basics.configuration.env.storageS3PathStyle')},
      {default: '-', configKey: 'storage.backend.s3.sseAlgorithm', desc: t('helpCenter.basics.configuration.env.storageS3Sse')},
      {default: '-', configKey: 'storage.backend.s3.basePath', desc: t('helpCenter.basics.configuration.env.storageS3BasePath')},
      {default: '-', configKey: 'storage.backend.smb.host', desc: t('helpCenter.basics.configuration.env.storageSmbHost')},
      {default: '445', configKey: 'storage.backend.smb.port', desc: t('helpCenter.basics.configuration.env.storageSmbPort')},
      {default: '-', configKey: 'storage.backend.smb.share', desc: t('helpCenter.basics.configuration.env.storageSmbShare')},
      {default: '-', configKey: 'storage.backend.smb.domain', desc: t('helpCenter.basics.configuration.env.storageSmbDomain')},
      {default: '-', configKey: 'storage.backend.smb.username', desc: t('helpCenter.basics.configuration.env.storageSmbUser')},
      {default: '-', configKey: 'storage.backend.smb.password', desc: t('helpCenter.basics.configuration.env.storageSmbPassword')},
      {default: '-', configKey: 'storage.backend.smb.basePath', desc: t('helpCenter.basics.configuration.env.storageSmbBasePath')},
      {default: 'true', configKey: 'storage.backend.smb.seal', desc: t('helpCenter.basics.configuration.env.storageSmbSeal')},
      {default: 'false', configKey: 'storage.backend.smb.dfs', desc: t('helpCenter.basics.configuration.env.storageSmbDfs')},
      {default: '-', configKey: 'storage.backend.sftp.host', desc: t('helpCenter.basics.configuration.env.storageSftpHost')},
      {default: '22', configKey: 'storage.backend.sftp.port', desc: t('helpCenter.basics.configuration.env.storageSftpPort')},
      {default: '-', configKey: 'storage.backend.sftp.username', desc: t('helpCenter.basics.configuration.env.storageSftpUser')},
      {default: '-', configKey: 'storage.backend.sftp.password', desc: t('helpCenter.basics.configuration.env.storageSftpPassword')},
      {default: '-', configKey: 'storage.backend.sftp.privateKey', desc: t('helpCenter.basics.configuration.env.storageSftpPrivateKey')},
      {default: '-', configKey: 'storage.backend.sftp.knownHostsFingerprint', desc: t('helpCenter.basics.configuration.env.storageSftpKnownHosts')},
      {default: '-', configKey: 'storage.backend.sftp.basePath', desc: t('helpCenter.basics.configuration.env.storageSftpBasePath')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupNetwork'),
    note: t('helpCenter.basics.configuration.envGroupNetworkNote'),
    defaultOpen: true,
    vars: [
      {name: 'NETWORK_TRUSTEDPROXIES', default: '-', configKey: 'network.trustedProxies', desc: t('helpCenter.basics.configuration.env.networkTrustedProxies')},
      {name: 'NETWORK_CLOUDFLARE', default: 'false', configKey: 'network.cloudflare', desc: t('helpCenter.basics.configuration.env.networkCloudflare')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupMetrics'),
    note: t('helpCenter.basics.configuration.envGroupMetricsNote'),
    defaultOpen: false,
    vars: [
      {name: 'METRICS_REQUESTSTATSRETENTIONDAYS', default: '3', configKey: 'metrics.requestStatsRetentionDays', desc: t('helpCenter.basics.configuration.env.metricsRequestRetention')},
      {name: 'METRICS_FEEDSTATSRETENTIONDAYS', default: '90', configKey: 'metrics.feedStatsRetentionDays', desc: t('helpCenter.basics.configuration.env.metricsFeedRetention')},
      {name: 'METRICS_TRAFFICENABLED', default: 'true', configKey: 'metrics.trafficEnabled', desc: t('helpCenter.basics.configuration.env.metricsTrafficEnabled')},
      {name: 'METRICS_TRAFFICRETENTIONDAYS', default: '90', configKey: 'metrics.trafficRetentionDays', desc: t('helpCenter.basics.configuration.env.metricsTrafficRetention')},
      {name: 'METRICS_TRAFFICFLUSHINTERVALSECONDS', default: '30', configKey: 'metrics.trafficFlushIntervalSeconds', desc: t('helpCenter.basics.configuration.env.metricsTrafficFlush')},
      {name: 'METRICS_WEBSTATSENABLED', default: 'true', configKey: 'metrics.webStatsEnabled', desc: t('helpCenter.basics.configuration.env.metricsWebStatsEnabled')},
      {name: 'METRICS_WEBSTATSRETENTIONDAYS', default: '365', configKey: 'metrics.webStatsRetentionDays', desc: t('helpCenter.basics.configuration.env.metricsWebStatsRetention')},
      {name: 'METRICS_WEBSTATSFLUSHINTERVALSECONDS', default: '30', configKey: 'metrics.webStatsFlushIntervalSeconds', desc: t('helpCenter.basics.configuration.env.metricsWebStatsFlush')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupFederation'),
    defaultOpen: true,
    vars: [
      {name: 'FEDERATION_ALLOWPRIVATEHOSTS', default: 'false', configKey: 'federation.allowPrivateHosts', desc: t('helpCenter.basics.configuration.env.federationAllowPrivateHosts')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupTheming'),
    defaultOpen: false,
    vars: [
      {name: 'THEMING_DEFAULTTHEME', default: 'ember', configKey: 'theming.defaultTheme', desc: t('helpCenter.basics.configuration.env.themingDefaultTheme')},
      {name: 'THEMING_DEFAULTFEEL', default: 'ROUNDED', configKey: 'theming.defaultFeel', desc: t('helpCenter.basics.configuration.env.themingDefaultFeel')},
      {name: 'THEMING_LOCKFEEL', default: 'false', configKey: 'theming.lockFeel', desc: t('helpCenter.basics.configuration.env.themingLockFeel')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupTools'),
    note: t('helpCenter.basics.configuration.envGroupToolsNote'),
    defaultOpen: false,
    vars: [
      {name: 'TYPST_BIN', default: 'typst', desc: t('helpCenter.basics.configuration.env.typstBin')},
      {name: 'PANDOC_BIN', default: 'pandoc', desc: t('helpCenter.basics.configuration.env.pandocBin')},
      {name: 'QPDF_BIN', default: 'qpdf', desc: t('helpCenter.basics.configuration.env.qpdfBin')},
      {name: 'CWEBP_BIN', default: 'cwebp', desc: t('helpCenter.basics.configuration.env.cwebpBin')},
      {name: 'LIBREOFFICE_BIN', default: 'libreoffice', desc: t('helpCenter.basics.configuration.env.libreOfficeBin')},
      {name: 'EMBER_PUBLIC_DIR', default: 'public', desc: t('helpCenter.basics.configuration.env.publicDir')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupFrontend'),
    note: t('helpCenter.basics.configuration.envGroupFrontendNote'),
    defaultOpen: false,
    vars: [
      {name: 'NUXT_PUBLIC_SITE_URL', default: '-', desc: t('helpCenter.basics.configuration.env.siteUrl')},
      {name: 'NUXT_PUBLIC_GOOGLE_SITE_VERIFICATION', default: '-', desc: t('helpCenter.basics.configuration.env.googleVerification')},
      {name: 'NUXT_API_BASE', default: 'http://localhost:8080', desc: t('helpCenter.basics.configuration.env.apiBase')},
      {name: 'NITRO_PORT', default: '3000', desc: t('helpCenter.basics.configuration.env.nitroPort')},
      {name: 'NITRO_HOST', default: '0.0.0.0', desc: t('helpCenter.basics.configuration.env.nitroHost')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupDemo'),
    note: t('helpCenter.basics.configuration.envGroupDemoNote'),
    defaultOpen: false,
    vars: [
      {name: 'DEMO_ENABLED', default: 'false', configKey: 'demo.enabled', desc: t('helpCenter.basics.configuration.env.demoEnabled')},
      {name: 'DEMO_DEV', default: 'false', configKey: 'demo.dev', desc: t('helpCenter.basics.configuration.env.demoDev')},
      {name: 'DEMO_IDLERESETMINUTES', default: '15', configKey: 'demo.idleResetMinutes', desc: t('helpCenter.basics.configuration.env.demoIdleReset')},
      {name: 'DEMO_FEDERATIONFORCEHTTP', default: 'false', configKey: 'demo.federationForceHttp', desc: t('helpCenter.basics.configuration.env.demoFederationHttp')},
    ],
  },
  {
    title: t('helpCenter.basics.configuration.envGroupDocker'),
    note: t('helpCenter.basics.configuration.envGroupDockerNote'),
    defaultOpen: false,
    vars: [
      {name: 'EMBER_TAG', default: 'latest', desc: t('helpCenter.basics.configuration.env.emberTag')},
      {name: 'EMBER_HOST', default: '-', desc: t('helpCenter.basics.configuration.env.emberHost')},
      {name: 'POSTGRES_DB', default: '-', desc: t('helpCenter.basics.configuration.env.postgresDb')},
      {name: 'POSTGRES_USER', default: '-', desc: t('helpCenter.basics.configuration.env.postgresUser')},
      {name: 'POSTGRES_PASSWORD', default: '-', desc: t('helpCenter.basics.configuration.env.postgresPassword')},
    ],
  },
]
</script>

<template>
  <EnvVarGroup
    v-for="g in groups"
    :key="g.title"
    :title="g.title"
    :note="g.note"
    :vars="g.vars"
    :default-open="g.defaultOpen"
  />
</template>
