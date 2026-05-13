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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersChanges.title')" :subtitle="t('helpCenter.membersChanges.subtitle')">
    <HelpSection :title="t('helpCenter.membersChanges.whatIs')">
      <p>{{ t('helpCenter.membersChanges.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersChanges.confirmTitle')">
      <p>{{ t('helpCenter.membersChanges.confirmText') }}</p>
    </HelpSection>

    <!-- Dummy: Change summary list -->
    <div class="space-y-3">
      <NeutralContainer>
        <div class="flex items-center justify-between flex-wrap gap-3 cursor-pointer">
          <div class="flex items-center gap-3">
            <font-awesome-icon :icon="['fas', 'chevron-down']" class="h-3 w-3 text-(--text-muted)"/>
            <font-awesome-icon :icon="['fas', 'user']" class="h-4 w-4 text-(--text-muted)"/>
            <div>
              <span class="font-semibold text-sm">Max Mustermann</span>
              <p class="text-xs text-(--text-muted)">
                {{ t('memberChanges.lastChange') }}: 14.05.2026, 15:30
              </p>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <ErrorBadge>2 {{ t('memberChanges.pending') }}</ErrorBadge>
            <SuccessButton class="text-xs">
              <font-awesome-icon :icon="['fas', 'check-double']" class="mr-1"/>
              {{ t('memberDetail.acknowledgeAll') }}
            </SuccessButton>
            <SecondaryButton class="text-xs">
              <font-awesome-icon :icon="['fas', 'user']" class="mr-1"/>
              {{ t('memberChanges.toProfile') }}
            </SecondaryButton>
          </div>
        </div>

        <!-- Expanded change -->
        <div class="mt-4 space-y-3">
          <div class="rounded-lg px-4 py-3 bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 border-l-4 border-primary space-y-2">
            <div class="flex items-center justify-between flex-wrap gap-2">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-semibold text-sm">Telefon</span>
                <span class="text-xs text-(--text-muted)">14.05.2026, 15:30</span>
                <span class="text-xs text-(--text-muted)">{{ t('memberDetail.changedBy') }}: Max Mustermann</span>
              </div>
              <ErrorBadge>{{ t('memberDetail.notAcknowledged') }}</ErrorBadge>
            </div>
            <div class="flex items-center gap-3 text-sm">
              <span class="text-(--text-muted)">0170 1111111</span>
              <font-awesome-icon :icon="['fas', 'chevron-right']" class="h-3 w-3 text-(--text-muted)"/>
              <span class="font-medium">0170 2222222</span>
            </div>
            <div class="flex items-center gap-2 pt-1">
              <PrimaryButton class="text-xs">
                <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                {{ t('memberDetail.acknowledge') }}
              </PrimaryButton>
              <SecondaryButton class="text-xs">
                <font-awesome-icon :icon="['fas', 'comment']" class="mr-1"/>
                {{ t('memberDetail.acknowledgeWithComment') }}
              </SecondaryButton>
            </div>
          </div>

          <div class="rounded-lg px-4 py-3 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20 space-y-2">
            <div class="flex items-center justify-between flex-wrap gap-2">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-semibold text-sm">Kleidergröße</span>
                <span class="text-xs text-(--text-muted)">13.05.2026, 10:00</span>
              </div>
              <SuccessBadge>
                <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                {{ t('memberDetail.acknowledged') }}
              </SuccessBadge>
            </div>
            <div class="flex items-center gap-3 text-sm">
              <span class="text-(--text-muted)">S</span>
              <font-awesome-icon :icon="['fas', 'chevron-right']" class="h-3 w-3 text-(--text-muted)"/>
              <span class="font-medium">M</span>
            </div>
          </div>
        </div>
      </NeutralContainer>
    </div>

    <HelpSection :title="t('helpCenter.membersChanges.badgeTitle')">
      <p>{{ t('helpCenter.membersChanges.badgeText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersChanges.tip') }}</HelpTip>
  </HelpArticle>
</template>
