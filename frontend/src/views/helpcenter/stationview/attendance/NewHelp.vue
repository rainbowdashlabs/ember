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
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.attendanceNew.title')" :subtitle="t('helpCenter.attendanceNew.subtitle')">
    <HelpSection :title="t('helpCenter.attendanceNew.howTo')">
      <p>{{ t('helpCenter.attendanceNew.howToText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceNew.fromEventTitle')">
      <p>{{ t('helpCenter.attendanceNew.fromEventText') }}</p>
    </HelpSection>

    <!-- Dummy: Today's events -->
    <SectionHeader>{{ t('attendanceNew.todayEvents') }}</SectionHeader>
    <div class="grid gap-3 sm:grid-cols-2">
      <PrimaryContainer class="space-y-2 cursor-pointer hover:ring-2 hover:ring-primary/30 transition-all">
        <div class="flex items-center justify-between">
          <span class="font-semibold">Übungsabend</span>
          <span class="text-sm">18:00 – 20:00</span>
        </div>
        <p class="text-xs text-(--text-muted)">{{ t('attendanceNew.template') }}: Übungsabend</p>
      </PrimaryContainer>
    </div>

    <HelpSection :title="t('helpCenter.attendanceNew.fromTemplateTitle')">
      <p>{{ t('helpCenter.attendanceNew.fromTemplateText') }}</p>
    </HelpSection>

    <!-- Dummy: Templates -->
    <SectionHeader>{{ t('attendanceNew.fromTemplate') }}</SectionHeader>
    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <NeutralContainer clickable>
        <div class="flex items-center justify-between">
          <span class="font-medium">Übungsabend</span>
          <PrimaryButton :icon="['fas', 'plus']">
            {{ t('attendanceNew.create') }}
          </PrimaryButton>
        </div>
      </NeutralContainer>
      <NeutralContainer clickable>
        <div class="flex items-center justify-between">
          <span class="font-medium">Jugenddienst</span>
          <PrimaryButton :icon="['fas', 'plus']">
            {{ t('attendanceNew.create') }}
          </PrimaryButton>
        </div>
      </NeutralContainer>
    </div>

    <HelpTip>{{ t('helpCenter.attendanceNew.tip') }}</HelpTip>
  </HelpArticle>
</template>
