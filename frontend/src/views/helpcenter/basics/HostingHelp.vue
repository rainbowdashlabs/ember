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

    <HelpSection :title="t('helpCenter.basics.hosting.config')">
      <p>{{ t('helpCenter.basics.hosting.configText') }}</p>
      <ul class="list-disc pl-5 space-y-1">
        <li><strong>{{ t('helpCenter.basics.hosting.configDb') }}:</strong> {{ t('helpCenter.basics.hosting.configDbText') }}</li>
        <li><strong>{{ t('helpCenter.basics.hosting.configMail') }}:</strong> {{ t('helpCenter.basics.hosting.configMailText') }}</li>
        <li><strong>{{ t('helpCenter.basics.hosting.configUrl') }}:</strong> {{ t('helpCenter.basics.hosting.configUrlText') }}</li>
        <li><strong>{{ t('helpCenter.basics.hosting.configAuth') }}:</strong> {{ t('helpCenter.basics.hosting.configAuthText') }}</li>
      </ul>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.hosting.reverseProxy')">
      <p>{{ t('helpCenter.basics.hosting.reverseProxyText') }}</p>
      <ul class="list-disc pl-5 space-y-1">
        <li>{{ t('helpCenter.basics.hosting.proxy1') }}</li>
        <li>{{ t('helpCenter.basics.hosting.proxy2') }}</li>
        <li>{{ t('helpCenter.basics.hosting.proxy3') }}</li>
      </ul>
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
      <ul class="list-disc pl-5 space-y-1">
        <li>{{ t('helpCenter.basics.hosting.backup1') }}</li>
        <li>{{ t('helpCenter.basics.hosting.backup2') }}</li>
      </ul>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.basics.hosting.tip') }}</HelpTip>
  </HelpArticle>
</template>
