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
import StationBadge from '@/components/badge/StationBadge.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import {ref} from 'vue'

const {t} = useI18n()

const showFederated = ref(true)
</script>

<template>
  <HelpArticle :title="t('helpCenter.federatedKb.title')" :subtitle="t('helpCenter.federatedKb.subtitle')">
    <HelpSection :title="t('helpCenter.federatedKb.whatIs')">
      <p>{{ t('helpCenter.federatedKb.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedKb.badgeTitle')">
      <p>{{ t('helpCenter.federatedKb.badgeText') }}</p>
    </HelpSection>

    <!-- Dummy: File tile with station badge -->
    <NeutralContainer>
      <div class="grid grid-cols-3 gap-3">
        <div class="flex flex-col items-center gap-2 p-3 rounded border border-bg-light-accent dark:border-bg-dark-accent">
          <font-awesome-icon :icon="['fas', 'file-lines']" class="text-2xl text-primary" />
          <span class="text-xs font-medium text-center">Grundlagen PSA</span>
          <StationBadge station-name="JF Musterstadt" />
          <span class="text-xs text-(--text-muted)">Schutzausrüstung</span>
        </div>
        <div class="flex flex-col items-center gap-2 p-3 rounded border border-bg-light-accent dark:border-bg-dark-accent">
          <font-awesome-icon :icon="['fas', 'file-lines']" class="text-2xl text-primary" />
          <span class="text-xs font-medium text-center">Knotenkunde</span>
          <StationBadge station-name="JF Beispieldorf" />
          <span class="text-xs text-(--text-muted)">Knoten & Stiche</span>
        </div>
        <div class="flex flex-col items-center gap-2 p-3 rounded border border-bg-light-accent dark:border-bg-dark-accent">
          <font-awesome-icon :icon="['fas', 'file-lines']" class="text-2xl text-(--text-muted)" />
          <span class="text-xs font-medium text-center">Willkommen</span>
          <span class="text-xs text-(--text-muted)">Eigene Datei</span>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.federatedKb.filterTitle')">
      <p>{{ t('helpCenter.federatedKb.filterText') }}</p>
    </HelpSection>

    <!-- Dummy: Toggle filter -->
    <NeutralContainer>
      <div class="flex flex-wrap items-center gap-2">
        <SelectionToggleButton :selected="showFederated" @toggle="showFederated = !showFederated">
          <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="w-3 h-3 mr-1" />
          {{ t('federation.shared') }}
        </SelectionToggleButton>
        <span class="text-xs text-(--text-muted)">{{ t('helpCenter.federatedKb.filterHint') }}</span>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.federatedKb.copyTitle')">
      <p>{{ t('helpCenter.federatedKb.copyText') }}</p>
    </HelpSection>

    <!-- Dummy: Copy action -->
    <NeutralContainer>
      <div class="flex items-center gap-3">
        <font-awesome-icon :icon="['fas', 'file-lines']" class="text-xl text-primary" />
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium">Grundlagen PSA</span>
            <StationBadge station-name="JF Musterstadt" />
          </div>
          <span class="text-xs text-(--text-muted)">Schutzausrüstung erklärt</span>
        </div>
        <IconButton :icon="['fas', 'copy']" :label="t('federation.copyToStation')" />
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.federatedKb.stationFilterTitle')">
      <p>{{ t('helpCenter.federatedKb.stationFilterText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.federatedKb.tip') }}</HelpTip>
  </HelpArticle>
</template>
