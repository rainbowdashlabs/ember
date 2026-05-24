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
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.attendancePast.title')" :subtitle="t('helpCenter.attendancePast.subtitle')">
    <HelpSection :title="t('helpCenter.attendancePast.whatShown')">
      <p>{{ t('helpCenter.attendancePast.whatShownText') }}</p>
      <p>{{ t('helpCenter.attendancePast.entryTitle') }}</p>
      <p>{{ t('helpCenter.attendancePast.entryCounts') }}</p>
    </HelpSection>

    <!-- Dummy: Past sessions list -->
    <div class="space-y-2">
      <NeutralContainer clickable>
        <div class="flex items-center justify-between flex-wrap gap-2">
          <div>
            <span class="font-semibold text-sm">Übungsabend</span>
            <MutedText size="sm" class="ml-3">10.05.2026</MutedText>
            <MutedText class="ml-2">18:00 – 20:00</MutedText>
          </div>
          <div class="flex items-center gap-2 text-xs">
            <SuccessBadge>18 {{ t('attendancePast.present') }}</SuccessBadge>
            <ErrorBadge>3 {{ t('attendancePast.absent') }}</ErrorBadge>
            <InfoBadge>1 {{ t('attendancePast.declined') }}</InfoBadge>
          </div>
        </div>
      </NeutralContainer>
      <NeutralContainer clickable>
        <div class="flex items-center justify-between flex-wrap gap-2">
          <div>
            <span class="font-semibold text-sm">Jugenddienst</span>
            <MutedText size="sm" class="ml-3">07.05.2026</MutedText>
            <MutedText class="ml-2">14:00 – 17:00</MutedText>
          </div>
          <div class="flex items-center gap-2 text-xs">
            <SuccessBadge>12 {{ t('attendancePast.present') }}</SuccessBadge>
            <ErrorBadge>2 {{ t('attendancePast.absent') }}</ErrorBadge>
            <InfoBadge>0 {{ t('attendancePast.declined') }}</InfoBadge>
            <SecondaryBadge>1 {{ t('attendancePast.unconfirmed') }}</SecondaryBadge>
          </div>
        </div>
      </NeutralContainer>
    </div>

    <HelpSection :title="t('helpCenter.attendancePast.detailTitle')">
      <p>{{ t('helpCenter.attendancePast.detailText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.attendancePast.tip') }}</HelpTip>
  </HelpArticle>
</template>
