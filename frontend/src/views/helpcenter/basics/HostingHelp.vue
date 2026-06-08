/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import BulletList from '@/components/typography/BulletList.vue'

const {t} = useI18n()

const envDb = [
  {name: 'DB_HOST', default: 'localhost', desc: t('helpCenter.basics.hosting.env.dbHost')},
  {name: 'DB_PORT', default: '5432', desc: t('helpCenter.basics.hosting.env.dbPort')},
  {name: 'DB_USER', default: 'ember', desc: t('helpCenter.basics.hosting.env.dbUser')},
  {name: 'DB_PASSWORD', default: 'ember', desc: t('helpCenter.basics.hosting.env.dbPassword')},
  {name: 'DB_DATABASE', default: 'ember', desc: t('helpCenter.basics.hosting.env.dbDatabase')},
  {name: 'DB_SCHEMA', default: 'ember', desc: t('helpCenter.basics.hosting.env.dbSchema')},
  {name: 'DB_POOLSIZE', default: '5', desc: t('helpCenter.basics.hosting.env.dbPoolSize')},
]

const envTools = [
  {name: 'TYPST_BIN', default: 'typst', desc: t('helpCenter.basics.hosting.env.typstBin')},
  {name: 'PANDOC_BIN', default: 'pandoc', desc: t('helpCenter.basics.hosting.env.pandocBin')},
  {name: 'EMBER_PUBLIC_DIR', default: 'public', desc: t('helpCenter.basics.hosting.env.publicDir')},
]

const envFrontend = [
  {name: 'NUXT_PUBLIC_SITE_URL', default: '—', desc: t('helpCenter.basics.hosting.env.siteUrl')},
  {name: 'NUXT_PUBLIC_GOOGLE_SITE_VERIFICATION', default: '—', desc: t('helpCenter.basics.hosting.env.googleVerification')},
  {name: 'NUXT_API_BASE', default: 'http://localhost:8080', desc: t('helpCenter.basics.hosting.env.apiBase')},
  {name: 'NITRO_PORT', default: '3000', desc: t('helpCenter.basics.hosting.env.nitroPort')},
  {name: 'NITRO_HOST', default: '0.0.0.0', desc: t('helpCenter.basics.hosting.env.nitroHost')},
]

const envApi = [
  {name: 'API_HOST', default: '0.0.0.0', desc: t('helpCenter.basics.hosting.env.apiHost')},
  {name: 'API_PORT', default: '8080', desc: t('helpCenter.basics.hosting.env.apiPort')},
  {name: 'API_BASEURL', default: 'http://localhost:5173', desc: t('helpCenter.basics.hosting.env.apiBaseUrl')},
  {name: 'API_MAXIMAGESIZEBYTES', default: '5242880', desc: t('helpCenter.basics.hosting.env.apiMaxImageSize')},
]

const envMailing = [
  {name: 'MAILING_PROVIDER', default: 'SMTP', desc: t('helpCenter.basics.hosting.env.mailingProvider')},
  {name: 'MAILING_SMTP', default: '—', desc: t('helpCenter.basics.hosting.env.mailingSmtp')},
  {name: 'MAILING_USER', default: '—', desc: t('helpCenter.basics.hosting.env.mailingUser')},
  {name: 'MAILING_PASSWORD', default: '—', desc: t('helpCenter.basics.hosting.env.mailingPassword')},
  {name: 'MAILING_SENDERADDRESS', default: '—', desc: t('helpCenter.basics.hosting.env.mailingSenderAddress')},
  {name: 'MAILING_SENDERNAME', default: 'Ember', desc: t('helpCenter.basics.hosting.env.mailingSenderName')},
  {name: 'MAILING_DAILYSENDLIMIT', default: '200', desc: t('helpCenter.basics.hosting.env.mailingDailyLimit')},
  {name: 'MAILING_NOTIFICATIONDIGESTINTERVALMINUTES', default: '60', desc: t('helpCenter.basics.hosting.env.mailingDigestInterval')},
]

const envAuth = [
  {name: 'AUTH_SESSIONMINUTES', default: '30', desc: t('helpCenter.basics.hosting.env.authSessionMinutes')},
  {name: 'AUTH_VERIFYTOKENHOURS', default: '24', desc: t('helpCenter.basics.hosting.env.authVerifyTokenHours')},
  {name: 'AUTH_PASSWORDTOKENHOURS', default: '72', desc: t('helpCenter.basics.hosting.env.authPasswordTokenHours')},
  {name: 'AUTH_TOKENBYTES', default: '32', desc: t('helpCenter.basics.hosting.env.authTokenBytes')},
]

const envTheming = [
  {name: 'THEMING_DEFAULTTHEME', default: 'ember', desc: t('helpCenter.basics.hosting.env.themingDefaultTheme')},
  {name: 'THEMING_DEFAULTFEEL', default: 'ROUNDED', desc: t('helpCenter.basics.hosting.env.themingDefaultFeel')},
  {name: 'THEMING_LOCKFEEL', default: 'false', desc: t('helpCenter.basics.hosting.env.themingLockFeel')},
]

const envDemo = [
  {name: 'DEMO_ENABLED', default: 'false', desc: t('helpCenter.basics.hosting.env.demoEnabled')},
  {name: 'DEMO_DEV', default: 'false', desc: t('helpCenter.basics.hosting.env.demoDev')},
  {name: 'DEMO_IDLERESETMINUTES', default: '15', desc: t('helpCenter.basics.hosting.env.demoIdleReset')},
  {name: 'DEMO_FEDERATIONFORCEHTTP', default: 'false', desc: t('helpCenter.basics.hosting.env.demoFederationHttp')},
]

const envDocker = [
  {name: 'EMBER_TAG', default: 'latest', desc: t('helpCenter.basics.hosting.env.emberTag')},
  {name: 'EMBER_HOST', default: '—', desc: t('helpCenter.basics.hosting.env.emberHost')},
  {name: 'POSTGRES_DB', default: '—', desc: t('helpCenter.basics.hosting.env.postgresDb')},
  {name: 'POSTGRES_USER', default: '—', desc: t('helpCenter.basics.hosting.env.postgresUser')},
  {name: 'POSTGRES_PASSWORD', default: '—', desc: t('helpCenter.basics.hosting.env.postgresPassword')},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.basics.hosting.title')" :subtitle="t('helpCenter.basics.hosting.subtitle')">
    <HelpSection :title="t('helpCenter.basics.hosting.whatNeeded')">
      <p>{{ t('helpCenter.basics.hosting.whatNeededText') }}</p>
      <div class="space-y-2 mt-3">
        <NeutralContainer class="flex items-start gap-3 p-3">
          <font-awesome-icon :icon="['fas', 'server']" class="h-4 w-4 text-primary mt-0.5 shrink-0"/>
          <div>
            <p class="font-semibold text-sm">{{ t('helpCenter.basics.hosting.req.server.name') }}</p>
            <p class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.req.server.desc') }}</p>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-start gap-3 p-3">
          <font-awesome-icon :icon="['fas', 'database']" class="h-4 w-4 text-primary mt-0.5 shrink-0"/>
          <div>
            <p class="font-semibold text-sm">{{ t('helpCenter.basics.hosting.req.database.name') }}</p>
            <p class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.req.database.desc') }}</p>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-start gap-3 p-3">
          <font-awesome-icon :icon="['fas', 'envelope']" class="h-4 w-4 text-primary mt-0.5 shrink-0"/>
          <div>
            <p class="font-semibold text-sm">{{ t('helpCenter.basics.hosting.req.mail.name') }}</p>
            <p class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.req.mail.desc') }}</p>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-start gap-3 p-3">
          <font-awesome-icon :icon="['fas', 'lock']" class="h-4 w-4 text-primary mt-0.5 shrink-0"/>
          <div>
            <p class="font-semibold text-sm">{{ t('helpCenter.basics.hosting.req.domain.name') }}</p>
            <p class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.req.domain.desc') }}</p>
          </div>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.hosting.docker')">
      <p>{{ t('helpCenter.basics.hosting.dockerText') }}</p>
      <NeutralContainer class="p-4 mt-3 font-mono text-xs leading-relaxed whitespace-pre-wrap bg-(--bg-accent)">{{ t('helpCenter.basics.hosting.dockerCompose') }}</NeutralContainer>
      <p class="mt-3">{{ t('helpCenter.basics.hosting.dockerText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.hosting.config')">
      <p>{{ t('helpCenter.basics.hosting.configText') }}</p>
      <BulletList>
        <li><strong>{{ t('helpCenter.basics.hosting.configDb') }}:</strong> {{ t('helpCenter.basics.hosting.configDbText') }}</li>
        <li><strong>{{ t('helpCenter.basics.hosting.configMail') }}:</strong> {{ t('helpCenter.basics.hosting.configMailText') }}</li>
        <li><strong>{{ t('helpCenter.basics.hosting.configUrl') }}:</strong> {{ t('helpCenter.basics.hosting.configUrlText') }}</li>
        <li><strong>{{ t('helpCenter.basics.hosting.configAuth') }}:</strong> {{ t('helpCenter.basics.hosting.configAuthText') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.hosting.envVars')">
      <p>{{ t('helpCenter.basics.hosting.envVarsText') }}</p>

      <p class="font-semibold text-sm mt-4 mb-2">{{ t('helpCenter.basics.hosting.envGroupDb') }}</p>
      <div class="space-y-2">
        <NeutralContainer v-for="v in envDb" :key="v.name" class="p-3">
          <div class="flex flex-wrap items-baseline gap-2">
            <code class="text-xs bg-[var(--bg-accent)] px-1.5 py-0.5 rounded font-bold">{{ v.name }}</code>
            <span class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.envDefault') }}: <code class="bg-[var(--bg-accent)] px-1 rounded">{{ v.default }}</code></span>
          </div>
          <p class="text-sm mt-1">{{ v.desc }}</p>
        </NeutralContainer>
      </div>

      <p class="font-semibold text-sm mt-4 mb-2">{{ t('helpCenter.basics.hosting.envGroupApi') }}</p>
      <div class="space-y-2">
        <NeutralContainer v-for="v in envApi" :key="v.name" class="p-3">
          <div class="flex flex-wrap items-baseline gap-2">
            <code class="text-xs bg-[var(--bg-accent)] px-1.5 py-0.5 rounded font-bold">{{ v.name }}</code>
            <span class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.envDefault') }}: <code class="bg-[var(--bg-accent)] px-1 rounded">{{ v.default }}</code></span>
          </div>
          <p class="text-sm mt-1">{{ v.desc }}</p>
        </NeutralContainer>
      </div>

      <p class="font-semibold text-sm mt-4 mb-2">{{ t('helpCenter.basics.hosting.envGroupMailing') }}</p>
      <div class="space-y-2">
        <NeutralContainer v-for="v in envMailing" :key="v.name" class="p-3">
          <div class="flex flex-wrap items-baseline gap-2">
            <code class="text-xs bg-[var(--bg-accent)] px-1.5 py-0.5 rounded font-bold">{{ v.name }}</code>
            <span class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.envDefault') }}: <code class="bg-[var(--bg-accent)] px-1 rounded">{{ v.default }}</code></span>
          </div>
          <p class="text-sm mt-1">{{ v.desc }}</p>
        </NeutralContainer>
      </div>

      <p class="font-semibold text-sm mt-4 mb-2">{{ t('helpCenter.basics.hosting.envGroupAuth') }}</p>
      <div class="space-y-2">
        <NeutralContainer v-for="v in envAuth" :key="v.name" class="p-3">
          <div class="flex flex-wrap items-baseline gap-2">
            <code class="text-xs bg-[var(--bg-accent)] px-1.5 py-0.5 rounded font-bold">{{ v.name }}</code>
            <span class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.envDefault') }}: <code class="bg-[var(--bg-accent)] px-1 rounded">{{ v.default }}</code></span>
          </div>
          <p class="text-sm mt-1">{{ v.desc }}</p>
        </NeutralContainer>
      </div>

      <p class="font-semibold text-sm mt-4 mb-2">{{ t('helpCenter.basics.hosting.envGroupTheming') }}</p>
      <div class="space-y-2">
        <NeutralContainer v-for="v in envTheming" :key="v.name" class="p-3">
          <div class="flex flex-wrap items-baseline gap-2">
            <code class="text-xs bg-[var(--bg-accent)] px-1.5 py-0.5 rounded font-bold">{{ v.name }}</code>
            <span class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.envDefault') }}: <code class="bg-[var(--bg-accent)] px-1 rounded">{{ v.default }}</code></span>
          </div>
          <p class="text-sm mt-1">{{ v.desc }}</p>
        </NeutralContainer>
      </div>

      <p class="font-semibold text-sm mt-4 mb-2">{{ t('helpCenter.basics.hosting.envGroupTools') }}</p>
      <div class="space-y-2">
        <NeutralContainer v-for="v in envTools" :key="v.name" class="p-3">
          <div class="flex flex-wrap items-baseline gap-2">
            <code class="text-xs bg-[var(--bg-accent)] px-1.5 py-0.5 rounded font-bold">{{ v.name }}</code>
            <span class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.envDefault') }}: <code class="bg-[var(--bg-accent)] px-1 rounded">{{ v.default }}</code></span>
          </div>
          <p class="text-sm mt-1">{{ v.desc }}</p>
        </NeutralContainer>
      </div>

      <p class="font-semibold text-sm mt-4 mb-2">{{ t('helpCenter.basics.hosting.envGroupFrontend') }}</p>
      <div class="space-y-2">
        <NeutralContainer v-for="v in envFrontend" :key="v.name" class="p-3">
          <div class="flex flex-wrap items-baseline gap-2">
            <code class="text-xs bg-[var(--bg-accent)] px-1.5 py-0.5 rounded font-bold">{{ v.name }}</code>
            <span class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.envDefault') }}: <code class="bg-[var(--bg-accent)] px-1 rounded">{{ v.default }}</code></span>
          </div>
          <p class="text-sm mt-1">{{ v.desc }}</p>
        </NeutralContainer>
      </div>

      <p class="font-semibold text-sm mt-4 mb-2">{{ t('helpCenter.basics.hosting.envGroupDemo') }}</p>
      <p class="text-sm mb-2">{{ t('helpCenter.basics.hosting.envGroupDemoNote') }}</p>
      <div class="space-y-2">
        <NeutralContainer v-for="v in envDemo" :key="v.name" class="p-3">
          <div class="flex flex-wrap items-baseline gap-2">
            <code class="text-xs bg-[var(--bg-accent)] px-1.5 py-0.5 rounded font-bold">{{ v.name }}</code>
            <span class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.envDefault') }}: <code class="bg-[var(--bg-accent)] px-1 rounded">{{ v.default }}</code></span>
          </div>
          <p class="text-sm mt-1">{{ v.desc }}</p>
        </NeutralContainer>
      </div>

      <p class="font-semibold text-sm mt-4 mb-2">{{ t('helpCenter.basics.hosting.envGroupDocker') }}</p>
      <p class="text-sm mb-2">{{ t('helpCenter.basics.hosting.envGroupDockerNote') }}</p>
      <div class="space-y-2">
        <NeutralContainer v-for="v in envDocker" :key="v.name" class="p-3">
          <div class="flex flex-wrap items-baseline gap-2">
            <code class="text-xs bg-[var(--bg-accent)] px-1.5 py-0.5 rounded font-bold">{{ v.name }}</code>
            <span class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.envDefault') }}: <code class="bg-[var(--bg-accent)] px-1 rounded">{{ v.default }}</code></span>
          </div>
          <p class="text-sm mt-1">{{ v.desc }}</p>
        </NeutralContainer>
      </div>

      <HelpTip>{{ t('helpCenter.basics.hosting.envTip') }}</HelpTip>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.hosting.dataDir')">
      <p>{{ t('helpCenter.basics.hosting.dataDirText') }}</p>
      <BulletList>
        <li>{{ t('helpCenter.basics.hosting.dataLegal') }}</li>
        <li>{{ t('helpCenter.basics.hosting.dataImages') }}</li>
        <li>{{ t('helpCenter.basics.hosting.dataKbFiles') }}</li>
      </BulletList>
      <p class="mt-2 text-sm">{{ t('helpCenter.basics.hosting.dataDirText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.hosting.reverseProxy')">
      <p>{{ t('helpCenter.basics.hosting.reverseProxyText') }}</p>
      <BulletList>
        <li>{{ t('helpCenter.basics.hosting.proxy1') }}</li>
        <li>{{ t('helpCenter.basics.hosting.proxy2') }}</li>
        <li>{{ t('helpCenter.basics.hosting.proxy3') }}</li>
      </BulletList>
    </HelpSection>

    <ErrorContainer class="text-sm">
      <p class="font-semibold">{{ t('helpCenter.basics.hosting.security') }}</p>
      <p>{{ t('helpCenter.basics.hosting.securityText') }}</p>
    </ErrorContainer>

    <HelpSection :title="t('helpCenter.basics.hosting.updates')">
      <p>{{ t('helpCenter.basics.hosting.updatesText') }}</p>
      <p>{{ t('helpCenter.basics.hosting.updatesText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.hosting.backups')">
      <p>{{ t('helpCenter.basics.hosting.backupsText') }}</p>
      <BulletList>
        <li>{{ t('helpCenter.basics.hosting.backup1') }}</li>
        <li>{{ t('helpCenter.basics.hosting.backup2') }}</li>
      </BulletList>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.basics.hosting.tip') }}</HelpTip>
  </HelpArticle>
</template>
