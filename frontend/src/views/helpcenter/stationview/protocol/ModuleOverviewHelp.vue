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

const {t} = useI18n()

const pages = [
  { icon: 'list', key: 'protocolList' },
  { icon: 'clipboard-list', key: 'runList' },
  { icon: 'clipboard-check', key: 'runDetail' },
  { icon: 'pen-to-square', key: 'grading' },
  { icon: 'chart-bar', key: 'evaluation' },
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.protocolModuleOverview.title')" :subtitle="t('helpCenter.protocolModuleOverview.subtitle')">
    <HelpSection :title="t('helpCenter.protocolModuleOverview.whatIs')">
      <p>{{ t('helpCenter.protocolModuleOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolModuleOverview.pagesTitle')">
      <p>{{ t('helpCenter.protocolModuleOverview.pagesText') }}</p>

      <div class="space-y-2">
        <NeutralContainer v-for="page in pages" :key="page.key">
          <div class="flex items-center gap-3">
            <font-awesome-icon :icon="['fas', page.icon]" class="w-5 h-5 text-[var(--primary)]" />
            <div>
              <div class="font-medium text-sm">{{ t(`helpCenter.protocolModuleOverview.page.${page.key}.name`) }}</div>
              <p class="text-xs text-(--text-muted)">{{ t(`helpCenter.protocolModuleOverview.page.${page.key}.desc`) }}</p>
            </div>
          </div>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocolModuleOverview.tip') }}</HelpTip>
  </HelpArticle>
</template>
