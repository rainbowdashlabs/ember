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
    title: t('helpCenter.basics.hosting.envGroupDb'),
    vars: [
      {name: 'DB_HOST', default: 'localhost', desc: t('helpCenter.basics.hosting.env.dbHost')},
      {name: 'DB_PORT', default: '5432', desc: t('helpCenter.basics.hosting.env.dbPort')},
      {name: 'DB_USER', default: 'ember', desc: t('helpCenter.basics.hosting.env.dbUser')},
      {name: 'DB_PASSWORD', default: 'ember', desc: t('helpCenter.basics.hosting.env.dbPassword')},
      {name: 'DB_DATABASE', default: 'ember', desc: t('helpCenter.basics.hosting.env.dbDatabase')},
      {name: 'DB_SCHEMA', default: 'ember', desc: t('helpCenter.basics.hosting.env.dbSchema')},
      {name: 'DB_POOLSIZE', default: '5', desc: t('helpCenter.basics.hosting.env.dbPoolSize')},
    ],
  },
  {
    title: t('helpCenter.basics.hosting.envGroupApi'),
    vars: [
      {name: 'API_HOST', default: '0.0.0.0', desc: t('helpCenter.basics.hosting.env.apiHost')},
      {name: 'API_PORT', default: '8080', desc: t('helpCenter.basics.hosting.env.apiPort')},
      {name: 'API_BASEURL', default: 'http://localhost:5173', desc: t('helpCenter.basics.hosting.env.apiBaseUrl')},
      {name: 'API_MAXIMAGESIZEBYTES', default: '5242880', desc: t('helpCenter.basics.hosting.env.apiMaxImageSize')},
    ],
  },
  {
    title: t('helpCenter.basics.hosting.envGroupMailing'),
    vars: [
      {name: 'MAILING_PROVIDER', default: 'SMTP', desc: t('helpCenter.basics.hosting.env.mailingProvider')},
      {name: 'MAILING_SMTP', default: '—', desc: t('helpCenter.basics.hosting.env.mailingSmtp')},
      {name: 'MAILING_USER', default: '—', desc: t('helpCenter.basics.hosting.env.mailingUser')},
      {name: 'MAILING_PASSWORD', default: '—', desc: t('helpCenter.basics.hosting.env.mailingPassword')},
      {name: 'MAILING_SENDERADDRESS', default: '—', desc: t('helpCenter.basics.hosting.env.mailingSenderAddress')},
      {name: 'MAILING_SENDERNAME', default: 'Ember', desc: t('helpCenter.basics.hosting.env.mailingSenderName')},
      {name: 'MAILING_DAILYSENDLIMIT', default: '200', desc: t('helpCenter.basics.hosting.env.mailingDailyLimit')},
      {name: 'MAILING_NOTIFICATIONDIGESTINTERVALMINUTES', default: '60', desc: t('helpCenter.basics.hosting.env.mailingDigestInterval')},
    ],
  },
  {
    title: t('helpCenter.basics.hosting.envGroupAuth'),
    vars: [
      {name: 'AUTH_SESSIONMINUTES', default: '30', desc: t('helpCenter.basics.hosting.env.authSessionMinutes')},
      {name: 'AUTH_VERIFYTOKENHOURS', default: '24', desc: t('helpCenter.basics.hosting.env.authVerifyTokenHours')},
      {name: 'AUTH_PASSWORDTOKENHOURS', default: '72', desc: t('helpCenter.basics.hosting.env.authPasswordTokenHours')},
      {name: 'AUTH_TOKENBYTES', default: '32', desc: t('helpCenter.basics.hosting.env.authTokenBytes')},
    ],
  },
  {
    title: t('helpCenter.basics.hosting.envGroupTheming'),
    vars: [
      {name: 'THEMING_DEFAULTTHEME', default: 'ember', desc: t('helpCenter.basics.hosting.env.themingDefaultTheme')},
      {name: 'THEMING_DEFAULTFEEL', default: 'ROUNDED', desc: t('helpCenter.basics.hosting.env.themingDefaultFeel')},
      {name: 'THEMING_LOCKFEEL', default: 'false', desc: t('helpCenter.basics.hosting.env.themingLockFeel')},
    ],
  },
  {
    title: t('helpCenter.basics.hosting.envGroupTools'),
    vars: [
      {name: 'TYPST_BIN', default: 'typst', desc: t('helpCenter.basics.hosting.env.typstBin')},
      {name: 'PANDOC_BIN', default: 'pandoc', desc: t('helpCenter.basics.hosting.env.pandocBin')},
      {name: 'EMBER_PUBLIC_DIR', default: 'public', desc: t('helpCenter.basics.hosting.env.publicDir')},
    ],
  },
  {
    title: t('helpCenter.basics.hosting.envGroupFrontend'),
    vars: [
      {name: 'NUXT_PUBLIC_SITE_URL', default: '—', desc: t('helpCenter.basics.hosting.env.siteUrl')},
      {name: 'NUXT_PUBLIC_GOOGLE_SITE_VERIFICATION', default: '—', desc: t('helpCenter.basics.hosting.env.googleVerification')},
      {name: 'NUXT_API_BASE', default: 'http://localhost:8080', desc: t('helpCenter.basics.hosting.env.apiBase')},
      {name: 'NITRO_PORT', default: '3000', desc: t('helpCenter.basics.hosting.env.nitroPort')},
      {name: 'NITRO_HOST', default: '0.0.0.0', desc: t('helpCenter.basics.hosting.env.nitroHost')},
    ],
  },
  {
    title: t('helpCenter.basics.hosting.envGroupDemo'),
    note: t('helpCenter.basics.hosting.envGroupDemoNote'),
    vars: [
      {name: 'DEMO_ENABLED', default: 'false', desc: t('helpCenter.basics.hosting.env.demoEnabled')},
      {name: 'DEMO_DEV', default: 'false', desc: t('helpCenter.basics.hosting.env.demoDev')},
      {name: 'DEMO_IDLERESETMINUTES', default: '15', desc: t('helpCenter.basics.hosting.env.demoIdleReset')},
      {name: 'DEMO_FEDERATIONFORCEHTTP', default: 'false', desc: t('helpCenter.basics.hosting.env.demoFederationHttp')},
    ],
  },
  {
    title: t('helpCenter.basics.hosting.envGroupDocker'),
    note: t('helpCenter.basics.hosting.envGroupDockerNote'),
    vars: [
      {name: 'EMBER_TAG', default: 'latest', desc: t('helpCenter.basics.hosting.env.emberTag')},
      {name: 'EMBER_HOST', default: '—', desc: t('helpCenter.basics.hosting.env.emberHost')},
      {name: 'POSTGRES_DB', default: '—', desc: t('helpCenter.basics.hosting.env.postgresDb')},
      {name: 'POSTGRES_USER', default: '—', desc: t('helpCenter.basics.hosting.env.postgresUser')},
      {name: 'POSTGRES_PASSWORD', default: '—', desc: t('helpCenter.basics.hosting.env.postgresPassword')},
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
  />
</template>
