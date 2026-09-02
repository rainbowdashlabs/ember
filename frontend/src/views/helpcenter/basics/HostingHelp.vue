/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import BulletList from '@/components/typography/BulletList.vue'
import HelpFeatureItem from '@/components/helpcenter/HelpFeatureItem.vue'

const {t} = useI18n()

/** The address this help page is being read at, so the command says where it fetches from. */
const origin = computed(() => (import.meta.client ? window.location.origin : 'https://ember-panel.de'))

const requirements = [
  {icon: ['fas', 'server'], key: 'server'},
  {icon: ['fas', 'database'], key: 'database'},
  {icon: ['fas', 'envelope'], key: 'mail'},
  {icon: ['fas', 'lock'], key: 'domain'},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.basics.hosting.title')" :subtitle="t('helpCenter.basics.hosting.subtitle')">
    <HelpSection :title="t('helpCenter.basics.hosting.whatNeeded')">
      <p>{{ t('helpCenter.basics.hosting.whatNeededText') }}</p>
      <div class="space-y-2 mt-3">
        <HelpFeatureItem
            v-for="req in requirements" :key="req.key" :icon="req.icon"
            :title="t(`helpCenter.basics.hosting.req.${req.key}.name`)"
            :description="t(`helpCenter.basics.hosting.req.${req.key}.desc`)"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.hosting.installer')">
      <p>{{ t('helpCenter.basics.hosting.installerText') }}</p>
      <NeutralContainer class="p-4 mt-3 font-mono text-xs bg-(--bg-accent)">
        curl -fsSL {{ origin }}/install.sh | bash
      </NeutralContainer>
      <p class="mt-3">{{ t('helpCenter.basics.hosting.installerPageText') }}</p>
      <router-link :to="{name: 'install'}">
        <NeutralContainer class="flex items-center gap-3 p-4 mt-3 cursor-pointer hover:bg-(--bg-accent) transition-colors">
          <font-awesome-icon :icon="['fas', 'code']" class="h-5 w-5 text-primary shrink-0"/>
          <div class="flex-1">
            <p class="font-semibold text-sm">{{ t('helpCenter.basics.hosting.installerPageTitle') }}</p>
            <p class="text-xs text-(--text-muted)">{{ t('helpCenter.basics.hosting.installerPageHint') }}</p>
          </div>
        </NeutralContainer>
      </router-link>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.hosting.docker')">
      <p>{{ t('helpCenter.basics.hosting.dockerText') }}</p>
      <NeutralContainer class="p-4 mt-3 font-mono text-xs leading-relaxed whitespace-pre-wrap bg-(--bg-accent)">{{ t('helpCenter.basics.hosting.dockerCompose') }}</NeutralContainer>
      <p class="mt-3">{{ t('helpCenter.basics.hosting.dockerText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.hosting.firstStart')">
      <p>{{ t('helpCenter.basics.hosting.firstStartText') }}</p>
      <BulletList>
        <li>{{ t('helpCenter.basics.hosting.firstStartStep1') }}</li>
        <li>{{ t('helpCenter.basics.hosting.firstStartStep2') }}</li>
        <li>{{ t('helpCenter.basics.hosting.firstStartStep3') }}</li>
      </BulletList>
      <HelpTip class="mt-3">{{ t('helpCenter.basics.hosting.firstStartWhy') }}</HelpTip>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.hosting.noMail')">
      <p>{{ t('helpCenter.basics.hosting.noMailText') }}</p>
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
        <li><code>data/documents/</code> - {{ t('helpCenter.basics.hosting.dataLegal') }}</li>
        <li><code>data/station/&lt;id&gt;/</code> - {{ t('helpCenter.basics.hosting.dataStation') }}</li>
        <li><code>data/account/&lt;id&gt;/</code> - {{ t('helpCenter.basics.hosting.dataAccount') }}</li>
        <li><code>data/inst/</code> - {{ t('helpCenter.basics.hosting.dataInst') }}</li>
        <li><code>data/discovery/</code> - {{ t('helpCenter.basics.hosting.dataDiscovery') }}</li>
        <li><code>data/maps/</code> - {{ t('helpCenter.basics.hosting.dataMaps') }}</li>
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
