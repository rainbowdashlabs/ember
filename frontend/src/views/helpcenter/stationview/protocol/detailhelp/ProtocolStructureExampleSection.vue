/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'

const { t } = useI18n()
</script>

<template>
  <HelpSection :title="t('helpCenter.protocolDetail.exampleTitle')">
    <div class="flex items-center gap-2 mb-4">
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'chevron-left']" />
      </SecondaryButton>
      <SectionHeader>Jugendflamme Stufe 1</SectionHeader>
      <EditButton disabled :label="t('common.edit')" />
      <span class="text-sm text-[var(--text-muted)] ml-auto">
        {{ t('protocol.threshold') }}: 25P / 34P {{ t('protocol.total') }}
      </span>
    </div>

    <div class="space-y-4">
      <NeutralContainer class="space-y-2">
        <div class="flex items-center gap-2">
          <SubHeader>Notruf</SubHeader>
          <MutedText class="ml-auto">8P</MutedText>
          <IconButton :icon="['fas', 'plus']" label="" disabled />
          <IconButton :icon="['fas', 'folder-plus']" label="" disabled />
          <IconButton :icon="['fas', 'pen']" label="" disabled />
          <DeleteButton disabled />
        </div>

        <div class="flex items-center gap-2 pl-4 text-xs">
          <MutedIcon :icon="['fas', 'square']" />
          <span class="flex-1">5 W-Fragen nennen</span>
          <span class="text-xs text-[var(--text-muted)]">5P</span>
          <IconButton :icon="['fas', 'pen']" label="" disabled />
          <DeleteButton disabled />
        </div>
        <div class="flex items-center gap-2 pl-4 text-xs">
          <MutedIcon :icon="['fas', 'square']" />
          <span class="flex-1">Notrufnummer 112</span>
          <span class="text-xs text-[var(--text-muted)]">2P</span>
          <IconButton :icon="['fas', 'pen']" label="" disabled />
          <DeleteButton disabled />
        </div>
        <div class="flex items-center gap-2 pl-4 text-xs">
          <MutedIcon :icon="['fas', 'square']" />
          <span class="flex-1">Notrufnummer 110</span>
          <span class="text-xs text-[var(--text-muted)]">1P</span>
          <IconButton :icon="['fas', 'pen']" label="" disabled />
          <DeleteButton disabled />
        </div>
      </NeutralContainer>

      <NeutralContainer class="space-y-2">
        <div class="flex items-center gap-2">
          <SubHeader>Knoten und Stiche</SubHeader>
          <MutedText class="ml-auto">11P</MutedText>
        </div>
      </NeutralContainer>

      <PrimaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('protocol.addSection') }}
      </PrimaryButton>
    </div>
  </HelpSection>
</template>
