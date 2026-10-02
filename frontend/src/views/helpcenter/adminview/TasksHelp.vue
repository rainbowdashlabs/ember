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
import HelpList from '@/components/helpcenter/HelpList.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TaskOutcomeBadge from '@/views/adminview/admintasksview/TaskOutcomeBadge.vue'
import {TaskOutcome} from '@/api/adminTasks'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminTasks.title')" :subtitle="t('helpCenter.adminTasks.subtitle')">
    <HelpSection :title="t('helpCenter.adminTasks.whatIs')">
      <p>{{ t('helpCenter.adminTasks.whatIsText') }}</p>
      <p>{{ t('helpCenter.adminTasks.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminTasks.columnsTitle')">
      <p>{{ t('helpCenter.adminTasks.columnsText') }}</p>
      <HelpList>
        <li>{{ t('helpCenter.adminTasks.columnName') }}</li>
        <li>{{ t('helpCenter.adminTasks.columnInterval') }}</li>
        <li>{{ t('helpCenter.adminTasks.columnOutcome') }}</li>
        <li>{{ t('helpCenter.adminTasks.columnStarted') }}</li>
        <li>{{ t('helpCenter.adminTasks.columnFailures') }}</li>
        <li>{{ t('helpCenter.adminTasks.columnMessage') }}</li>
      </HelpList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminTasks.outcomesTitle')">
      <p>{{ t('helpCenter.adminTasks.outcomesText') }}</p>
      <NeutralContainer class="space-y-2">
        <div class="flex flex-wrap items-center gap-2">
          <TaskOutcomeBadge :outcome="TaskOutcome.SUCCEEDED"/>
          <span class="text-sm">{{ t('helpCenter.adminTasks.outcomeSucceeded') }}</span>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <TaskOutcomeBadge :outcome="TaskOutcome.RUNNING"/>
          <span class="text-sm">{{ t('helpCenter.adminTasks.outcomeRunning') }}</span>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <TaskOutcomeBadge :outcome="TaskOutcome.FAILED"/>
          <span class="text-sm">{{ t('helpCenter.adminTasks.outcomeFailed') }}</span>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <TaskOutcomeBadge :outcome="TaskOutcome.NOT_RUN_YET"/>
          <span class="text-sm">{{ t('helpCenter.adminTasks.outcomeNotRunYet') }}</span>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminTasks.refreshTitle')">
      <p>{{ t('helpCenter.adminTasks.refreshText') }}</p>
      <SecondaryButton :icon="['fas', 'rotate']">{{ t('common.refresh') }}</SecondaryButton>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminTasks.restartTitle')">
      <p>{{ t('helpCenter.adminTasks.restartText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminTasks.tip') }}</HelpTip>
  </HelpArticle>
</template>
