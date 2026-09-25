/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import type {StationMember} from '@/api/types'
import ExportModal from './ExportModal.vue'
import MemberFilterBar from './FilterBar.vue'
import MemberTable from './MemberTable.vue'
import type {MemberListConfig} from './useMemberListConfig'

/**
 * The member list, drawn from a configuration: the tabs, the filters above the table, the table
 * and the export. A screen says which configuration it is drawing and nothing else.
 */
const props = defineProps<{
  config: MemberListConfig
}>()

const emit = defineEmits<{
  resendSetup: [member: StationMember]
}>()

const c = props.config
</script>

<template>
  <Spinner v-if="c.loading.value" size="lg"/>
  <FailureAlert :failure="c.failure.value ?? c.filterFailure.value"/>

  <div v-if="!c.loading.value" class="space-y-4">
    <TabBar v-model="c.activeTab.value" :tabs="c.tabs.value"/>
    <MemberFilterBar :config="config"/>
    <MemberTable :config="config" @resend-setup="(member) => emit('resendSetup', member)"/>
  </div>

  <ExportModal
      v-model="c.exporting.showExportModal.value"
      :columns="c.exporting.columnOptions.value"
      :selected-columns="c.exporting.selectedColumns.value"
      :selected-count="c.exporting.selectedIds.value.size"
      @export="c.performExport"
      @select-columns="c.exporting.selectColumns"
      @toggle-column="c.exporting.toggleColumn"
  />
</template>
