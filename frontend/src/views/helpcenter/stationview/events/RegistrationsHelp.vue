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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventsRegistrations.title')" :subtitle="t('helpCenter.eventsRegistrations.subtitle')">
    <HelpSection :title="t('helpCenter.eventsRegistrations.whatShown')">
      <p>{{ t('helpCenter.eventsRegistrations.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventsRegistrations.badgeTitle')">
      <p>{{ t('helpCenter.eventsRegistrations.badgeText') }}</p>
    </HelpSection>

    <!-- Dummy: Pending registrations list -->
    <SectionHeader>{{ t('eventsRegistrations.title') }}</SectionHeader>
    <div class="space-y-3">
      <NeutralContainer class="flex items-center justify-between">
        <div>
          <span class="font-medium">Max Mustermann</span>
          <span class="ml-2 text-sm text-(--text-muted)">Wettkampf Vorbereitung</span>
          <span class="ml-2 text-xs text-(--text-muted)">2026-05-25</span>
        </div>
        <div class="flex items-center gap-2">
          <PrimaryButton>
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
            {{ t('eventsRegistrations.accept') }}
          </PrimaryButton>
          <ErrorButton>
            <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
            {{ t('eventsRegistrations.deny') }}
          </ErrorButton>
        </div>
      </NeutralContainer>
      <NeutralContainer class="flex items-center justify-between">
        <div>
          <span class="font-medium">Erika Muster</span>
          <span class="ml-2 text-sm text-(--text-muted)">Wettkampf Vorbereitung</span>
          <span class="ml-2 text-xs text-(--text-muted)">2026-05-25</span>
        </div>
        <div class="flex items-center gap-2">
          <PrimaryButton>
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
            {{ t('eventsRegistrations.accept') }}
          </PrimaryButton>
          <ErrorButton>
            <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
            {{ t('eventsRegistrations.deny') }}
          </ErrorButton>
        </div>
      </NeutralContainer>
    </div>

    <HelpTip>{{ t('helpCenter.eventsRegistrations.tip') }}</HelpTip>
  </HelpArticle>
</template>
