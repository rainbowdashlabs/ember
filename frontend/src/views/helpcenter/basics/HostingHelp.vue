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

    <HelpSection :title="t('helpCenter.basics.hosting.configLinkTitle')">
      <p>{{ t('helpCenter.basics.hosting.configLinkText') }}</p>
      <router-link :to="{name: 'help-basics-hosting-configuration'}">
        <NeutralContainer class="flex items-center gap-3 p-4 mt-3 cursor-pointer hover:bg-(--bg-accent) transition-colors">
          <font-awesome-icon :icon="['fas', 'sliders']" class="h-5 w-5 text-primary shrink-0"/>
          <div class="flex-1">
            <p class="font-semibold text-sm">{{ t('helpCenter.basics.configuration.title') }}</p>
            <p class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.configuration.subtitle') }}</p>
          </div>
          <font-awesome-icon :icon="['fas', 'chevron-right']" class="h-3 w-3 text-(--text-muted)"/>
        </NeutralContainer>
      </router-link>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.hosting.dataDir')">
      <p>{{ t('helpCenter.basics.hosting.dataDirText') }}</p>
      <BulletList>
        <li><code>data/documents/</code> — {{ t('helpCenter.basics.hosting.dataLegal') }}</li>
        <li><code>data/station/&lt;id&gt;/</code> — {{ t('helpCenter.basics.hosting.dataStation') }}</li>
        <li><code>data/account/&lt;id&gt;/</code> — {{ t('helpCenter.basics.hosting.dataAccount') }}</li>
        <li><code>data/inst/</code> — {{ t('helpCenter.basics.hosting.dataInst') }}</li>
        <li><code>data/discovery/</code> — {{ t('helpCenter.basics.hosting.dataDiscovery') }}</li>
        <li><code>data/maps/</code> — {{ t('helpCenter.basics.hosting.dataMaps') }}</li>
      </BulletList>
      <p class="mt-2 text-sm">{{ t('helpCenter.basics.hosting.dataDirText2') }}</p>
      <p class="mt-2 text-sm">{{ t('helpCenter.basics.hosting.dataDirText3') }}</p>
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
        <li><strong>{{ t('helpCenter.basics.hosting.backupDbTitle') }}:</strong> {{ t('helpCenter.basics.hosting.backupDb') }}</li>
        <li><strong>{{ t('helpCenter.basics.hosting.backupConfigTitle') }}:</strong> {{ t('helpCenter.basics.hosting.backupConfig') }}</li>
        <li><strong>{{ t('helpCenter.basics.hosting.backupDataTitle') }}:</strong> {{ t('helpCenter.basics.hosting.backupData') }}</li>
        <li><strong>{{ t('helpCenter.basics.hosting.backupSecretsTitle') }}:</strong> {{ t('helpCenter.basics.hosting.backupSecrets') }}</li>
      </BulletList>
      <p class="mt-2 text-sm">{{ t('helpCenter.basics.hosting.backupsRemoteText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.basics.hosting.tip') }}</HelpTip>
  </HelpArticle>
</template>
