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
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.discovery.title')" :subtitle="t('helpCenter.discovery.subtitle')">
    <HelpSection :title="t('helpCenter.discovery.whatIs')">
      <p>{{ t('helpCenter.discovery.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.discovery.connectTitle')">
      <p>{{ t('helpCenter.discovery.connectText') }}</p>
    </HelpSection>

    <!-- Dummy: Discovery cards -->
    <HelpSection :title="t('helpCenter.discovery.exampleTitle')">
      <p>{{ t('helpCenter.discovery.exampleText') }}</p>
    </HelpSection>

    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <NeutralContainer class="flex flex-col gap-2">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-full bg-[var(--bg-accent)] flex items-center justify-center">
            <font-awesome-icon :icon="['fas', 'building']" class="text-[var(--text-muted)]"/>
          </div>
          <div class="min-w-0 flex-1">
            <div class="font-medium truncate">JF Musterstadt</div>
          </div>
        </div>
        <MutedText size="sm">Jugendfeuerwehr im Herzen von Musterstadt</MutedText>
        <div class="flex items-center gap-2 flex-wrap mt-auto pt-2">
          <PrimaryBadge>{{ t('discovery.ownStation') }}</PrimaryBadge>
        </div>
      </NeutralContainer>

      <NeutralContainer class="flex flex-col gap-2">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-full bg-[var(--bg-accent)] flex items-center justify-center">
            <font-awesome-icon :icon="['fas', 'building']" class="text-[var(--text-muted)]"/>
          </div>
          <div class="min-w-0 flex-1">
            <div class="font-medium truncate">JF Beispieldorf</div>
          </div>
        </div>
        <MutedText size="sm">Freiwillige Feuerwehr Beispieldorf</MutedText>
        <div class="flex items-center gap-2 flex-wrap mt-auto pt-2">
          <SuccessBadge>{{ t('discovery.alreadyConnected') }}</SuccessBadge>
        </div>
      </NeutralContainer>

      <NeutralContainer class="flex flex-col gap-2">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-full bg-[var(--bg-accent)] flex items-center justify-center">
            <font-awesome-icon :icon="['fas', 'building']" class="text-[var(--text-muted)]"/>
          </div>
          <div class="min-w-0 flex-1">
            <div class="font-medium truncate">JF Altenburg</div>
          </div>
        </div>
        <div class="flex items-center gap-2 flex-wrap mt-auto pt-2">
          <PrimaryButton compact disabled>
            <font-awesome-icon :icon="['fas', 'handshake']" class="mr-1"/>
            {{ t('discovery.connect') }}
          </PrimaryButton>
        </div>
      </NeutralContainer>
    </div>

    <HelpSection :title="t('helpCenter.discovery.settingsTitle')">
      <p>{{ t('helpCenter.discovery.settingsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.discovery.tip') }}</HelpTip>
  </HelpArticle>
</template>
