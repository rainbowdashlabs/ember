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
import TabBar from '@/components/navigation/TabBar.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import {ref} from 'vue'

const {t} = useI18n()

const activeTab = ref('pending')
const tabs = [
  {key: 'pending', label: t('memberChanges.tabPending')},
  {key: 'history', label: t('memberChanges.tabHistory')},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersChanges.title')" :subtitle="t('helpCenter.membersChanges.subtitle')">
    <HelpSection :title="t('helpCenter.membersChanges.whatIs')">
      <p>{{ t('helpCenter.membersChanges.whatIsText') }}</p>
    </HelpSection>

    <!-- Dummy: Tab bar for pending/history -->
    <HelpSection :title="t('helpCenter.membersChanges.tabsTitle')">
      <p>{{ t('helpCenter.membersChanges.tabsText') }}</p>
      <TabBar :model-value="activeTab" :tabs="tabs" class="mt-2"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersChanges.confirmTitle')">
      <p>{{ t('helpCenter.membersChanges.confirmText') }}</p>
    </HelpSection>

    <!-- Dummy: Change summary list (pending tab) -->
    <HelpSection :title="t('helpCenter.membersChanges.exampleTitle')">
      <div class="space-y-3">
        <NeutralContainer>
          <div class="flex items-center justify-between flex-wrap gap-2 cursor-pointer">
            <div class="flex items-center gap-2">
              <MutedIcon :icon="['fas', 'chevron-down']"/>
              <div>
                <span class="font-semibold text-sm">Max Mustermann</span>
                <p class="text-xs text-(--text-muted)">
                  {{ t('memberChanges.lastChange') }}: 14.05.2026, 15:30
                </p>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <ErrorBadge>2 {{ t('memberChanges.pending') }}</ErrorBadge>
              <SuccessButton :icon="['fas', 'check-double']">
                {{ t('memberDetail.acknowledgeAll') }}
              </SuccessButton>
              <SecondaryButton :icon="['fas', 'user']">
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
              <div class="flex items-center gap-2 text-xs">
                <span class="text-(--text-muted)">0170 1111111</span>
                <MutedIcon :icon="['fas', 'chevron-right']"/>
                <span class="font-medium">0170 2222222</span>
              </div>
              <div class="flex items-center gap-2 pt-1">
                <PrimaryButton :icon="['fas', 'check']">
                  {{ t('memberDetail.acknowledge') }}
                </PrimaryButton>
                <SecondaryButton :icon="['fas', 'comment']">
                  {{ t('memberDetail.acknowledgeWithComment') }}
                </SecondaryButton>
              </div>

              <!-- Dummy: Comment text area for acknowledge-with-comment -->
              <div class="space-y-2 pt-1">
                <TextAreaInput
                    :model-value="''"
                    :placeholder="t('memberDetail.commentPlaceholder')"
                    class="text-sm"
                />
                <PrimaryButton>
                  {{ t('memberDetail.submitAcknowledge') }}
                </PrimaryButton>
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
              <div class="flex items-center gap-2 text-xs">
                <span class="text-(--text-muted)">S</span>
                <MutedIcon :icon="['fas', 'chevron-right']"/>
                <span class="font-medium">M</span>
              </div>
              <!-- Dummy: Acknowledgement details -->
              <div class="text-xs text-(--text-muted)">
                <div class="flex items-center gap-1">
                  <font-awesome-icon :icon="['fas', 'check']" class="h-3 w-3 text-success"/>
                  <span>Anna Schmidt (13.05.2026, 14:15)</span>
                </div>
              </div>
            </div>
          </div>
        </NeutralContainer>
      </div>
    </HelpSection>

    <!-- History tab description -->
    <HelpSection :title="t('helpCenter.membersChanges.historyTitle')">
      <p>{{ t('helpCenter.membersChanges.historyText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersChanges.badgeTitle')">
      <p>{{ t('helpCenter.membersChanges.badgeText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersChanges.tip') }}</HelpTip>
  </HelpArticle>
</template>
