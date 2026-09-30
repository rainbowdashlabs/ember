/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import MutedText from '@/components/typography/MutedText.vue'
import HelpCenterHint from '@/components/help/HelpCenterHint.vue'
import {adminTasks} from '@/api'
import type {TaskStatus} from '@/api/adminTasks'
import {useDataTable} from '@/composables/useDataTable'
import {useConfigPanel} from '@/composables/useConfigPanel'
import TaskStatusTable from './admintasksview/TaskStatusTable.vue'
import {taskColumns} from './admintasksview/taskColumns'

/**
 * The instance's background tasks: the sweeps, reminders and flushes that run on their own, with how
 * each one's last run went.
 *
 * <p>What is shown is kept in memory since the last start, so a restart begins every task again at
 * "not run yet". Reloading asks again rather than watching, because nothing here changes faster than a
 * person reads it.
 */
const {t} = useI18n()

const {config: tasks, loading, failure, reload} = useConfigPanel<TaskStatus[]>({
  initial: [],
  fetch: () => adminTasks.listTasks(),
})

const table = useDataTable<TaskStatus>({
  id: 'admin-tasks',
  perStation: false,
  rows: tasks,
  columns: computed(() => taskColumns(t)),
  rowKey: task => task.name,
})
</script>

<template>
  <ViewContent :title="t('pages.admin-tasks.title')" :subtitle="t('pages.admin-tasks.subtitle')">
    <div class="space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <MutedText tag="p" size="sm">{{ t('adminTasks.sinceStart') }}</MutedText>
        <HelpCenterHint :to="{name: 'help-admin-tasks'}">
          {{ t('adminTasks.help') }}
        </HelpCenterHint>
      </div>
      <FailureAlert :failure="failure"/>
      <Spinner v-if="loading" size="lg"/>
      <template v-else>
        <div class="flex flex-wrap items-center justify-between gap-3">
          <SecondaryButton :icon="['fas', 'rotate']" @click="reload">
            {{ t('common.refresh') }}
          </SecondaryButton>
          <TableColumnPicker :table="table"/>
        </div>
        <TaskStatusTable :table="table"/>
      </template>
    </div>
  </ViewContent>
</template>
