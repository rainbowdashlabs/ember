/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import PendingMemberCard from '@/views/stationview/members/changesview/PendingMemberCard.vue'
import type {ChangeEntry} from '@/api/profileFieldChanges'
import {formatDateTime} from '@/util/format'
import {demoChanges, demoChangeSummary} from './fixtures'

const {t} = useI18n()

const summary = demoChangeSummary(t)
const changes = demoChanges(t)
const comment = ref('')

/** In the example, a change counts as the reader's own acknowledgement once anybody acknowledged it. */
function acknowledged(change: ChangeEntry): boolean {
  return change.acknowledgements.length > 0
}

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

    <HelpSection :title="t('helpCenter.membersChanges.tabsTitle')">
      <p>{{ t('helpCenter.membersChanges.tabsText') }}</p>
      <TabBar :model-value="activeTab" :tabs="tabs" class="mt-2"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersChanges.confirmTitle')">
      <p>{{ t('helpCenter.membersChanges.confirmText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersChanges.exampleTitle')">
      <div class="space-y-3">
        <PendingMemberCard v-model:acknowledge-comment="comment" :summary="summary" expanded
                           :member-changes="changes" :loading-changes="false" :acknowledging="false"
                           :show-comment-for-change-id="1" :is-acknowledged-by-me="acknowledged"
                           :format-date="formatDateTime"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersChanges.historyTitle')">
      <p>{{ t('helpCenter.membersChanges.historyText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersChanges.badgeTitle')">
      <p>{{ t('helpCenter.membersChanges.badgeText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersChanges.tip') }}</HelpTip>
  </HelpArticle>
</template>
