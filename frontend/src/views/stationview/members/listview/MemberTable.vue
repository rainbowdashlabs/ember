/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {RouteLocationRaw} from 'vue-router'
import EmptyState from '@/components/feedback/EmptyState.vue'
import FieldValueDisplay from '@/components/display/FieldValueDisplay.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import type {CellValue} from '@/components/table/tableColumn'
import type {ProfileField} from '@/api/profileFields'
import type {StationMember} from '@/api/types'
import {memberDisplayName} from './useMemberData'
import {roleOf} from './memberColumns'
import MemberExpansion from './MemberExpansion.vue'
import MemberNameCell from './MemberNameCell.vue'
import MemberRowLead from './MemberRowLead.vue'
import MemberTypeBadge from './MemberTypeBadge.vue'
import {useMemberRowExtras} from './memberRowExtras'
import type {MemberListConfig} from './useMemberListConfig'

/**
 * The member list's table. A press on a row opens it up to show the person's overview and who
 * manages them, or ticks it while choosing whom to export.
 */
const props = defineProps<{
  config: MemberListConfig
}>()

const emit = defineEmits<{
  resendSetup: [member: StationMember]
}>()

const {t} = useI18n()
const extras = useMemberRowExtras()

const c = props.config
const table = c.table
const exporting = c.exporting

const exportMode = computed(() => exporting.exportMode.value)
const fieldsById = computed(() => new Map(c.fields.value.map(field => [String(field.id), field])))

/** The questions that show as columns, which are the ones whose cells draw an answer. */
const shownFields = computed(() => table.visibleColumns
    .map(column => fieldsById.value.get(column.key))
    .filter((field): field is ProfileField => field !== undefined))

const allSelected = computed(() =>
    table.rows.length > 0 && table.rows.every(member => exporting.selectedIds.value.has(member.id)))

function overviewFieldsOf(memberId: number): ProfileField[] {
  const role = roleOf(c.memberRolesMap.value.get(memberId) ?? [])
  return c.overviewFields.value.filter(field => c.isAskedOf(field.id, role))
}

function managerName(manager: StationMember): string {
  const known = c.members.value.find(member => member.id === manager.id)
  return known ? memberDisplayName(known) : `#${manager.id}`
}

function answersOf(member: StationMember): ReadonlyMap<number, CellValue> | undefined {
  return c.answerCells.value.get(member.id)
}

function rowClass(member: StationMember): string {
  if (exportMode.value) return exporting.selectedIds.value.has(member.id) ? 'bg-primary/5' : ''
  return c.expandedId.value === member.id ? 'bg-bg-light-accent/30 dark:bg-bg-dark-accent/30' : ''
}

/**
 * Where a person's name leads: their own page, unless the list is choosing whom to export or this
 * reader may not reach that person at all.
 */
function namePageOf(member: StationMember): RouteLocationRaw | null {
  if (exportMode.value || extras.blockedReason(member.id)) return null
  return c.detailRouteOf(member)
}

function onRowClick(member: StationMember) {
  if (exportMode.value) exporting.toggleRow(member.id)
  else c.toggleExpand(member)
}
</script>

<template>
  <RecordTable :row-class="rowClass" :table="table" clickable row-test-id="member-row" test-id="member-table" @row-click="onRowClick">
    <template #lead-head>
      <CheckboxInput v-if="exportMode" :model-value="allSelected" data-testid="member-select-all" @update:model-value="exporting.toggleAllRows"/>
    </template>
    <template #lead="{row}">
      <MemberRowLead
          :can-edit="c.canEdit.value"
          :export-mode="exportMode"
          :member="row"
          :selected="exporting.selectedIds.value.has(row.id)"
          @navigate-detail="c.navigateToDetail(row, $event)"
          @navigate-edit="c.navigateToEdit(row, $event)"
          @toggle-select="exporting.toggleRow(row.id)"
      />
    </template>
    <template #cell-name="{row}">
      <RowLink :to="namePageOf(row)">
        <MemberNameCell :can-edit="c.canEdit.value" :member="row" @resend-setup="emit('resendSetup', row)"/>
      </RowLink>
    </template>
    <template #cell-userType="{row}">
      <MemberTypeBadge :user-type="row.userType"/>
    </template>
    <template v-for="field in shownFields" :key="field.id" #[`cell-${field.id}`]="{row}">
      <FieldValueDisplay v-if="answersOf(row)?.has(field.id)" :config="field.config" :field-type="field.fieldType" :value="answersOf(row)?.get(field.id)"/>
    </template>
    <template #after-row="{row, span}">
      <MemberExpansion
          v-if="!exportMode && c.expandedId.value === row.id"
          :col-span="span"
          :get-field-value-for="c.getFieldValue"
          :get-overview-fields-for="overviewFieldsOf"
          :manager-name="managerName"
          :managers="c.memberManagers.value.get(row.id) ?? []"
          :member="row"
          :overview-fields="overviewFieldsOf(row.id)"
      />
    </template>
    <template #empty>
      <EmptyState>{{ t('membersList.empty') }}</EmptyState>
    </template>
  </RecordTable>
</template>
