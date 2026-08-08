/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import EditButton from '@/components/button/EditButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import OverviewEditFields from './overviewsection/OverviewEditFields.vue'
import OverviewEditActions from './overviewsection/OverviewEditActions.vue'
import type { WaitingList, WaitingListField } from '@/api/waitingList'
import type { MemberGroup } from '@/api/types'
import { ref, computed } from 'vue'
import { waitingList as waitingListApi } from '@/api'
import { apiErrorMessage } from '@/util/apiError'

const props = defineProps<{
  list: WaitingList
  listId: number
  fields: WaitingListField[]
  groups: MemberGroup[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  updated: [list: WaitingList]
  error: [msg: string]
  success: [msg: string]
}>()

const { t } = useI18n()

const editing = ref(false)
const editName = ref('')
const editDescription = ref('')
const editScoringFormula = ref('')
const editConfirmInterval = ref(0)
const editTestingGroupId = ref<number | null>(null)
const editJoinGroupId = ref<number | null>(null)
const editAttendanceThreshold = ref(5)
const editIsPublic = ref(false)

const canSave = computed(() => !!editName.value.trim())

function startEditing() {
  editName.value = props.list.name
  editDescription.value = props.list.description ?? ''
  editScoringFormula.value = props.list.scoringFormula ?? ''
  editConfirmInterval.value = props.list.confirmIntervalDays ?? 0
  editTestingGroupId.value = props.list.testingGroupId ?? null
  editJoinGroupId.value = props.list.joinGroupId ?? null
  editAttendanceThreshold.value = props.list.attendanceThreshold ?? 5
  editIsPublic.value = props.list.isPublic ?? false
  editing.value = true
}

function cancelEditing() {
  editing.value = false
}

async function saveEditing() {
  if (!canSave.value) return
  try {
    const updated = await waitingListApi.update(props.listId, {
      name: editName.value.trim(),
      description: editDescription.value.trim(),
      scoringFormula: editScoringFormula.value.trim() || undefined,
      confirmIntervalDays: editConfirmInterval.value || undefined,
      testingGroupId: editTestingGroupId.value,
      joinGroupId: editJoinGroupId.value,
      attendanceThreshold: editAttendanceThreshold.value,
      isPublic: editIsPublic.value,
    })
    editing.value = false
    emit('updated', updated)
    emit('success', t('waitingList.saved'))
  } catch (e: unknown) {
    emit('error', apiErrorMessage(e) || t('common.error'))
    throw e
  }
}

function groupName(groupId: number | null | undefined): string {
  if (!groupId) return '-'
  return props.groups.find(g => g.id === groupId)?.name ?? '-'
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('waitingList.overview') }}</SubHeader>
      <EditButton v-if="!editing && !readonly" @click="startEditing" />
    </div>

    <template v-if="!editing">
      <div class="grid gap-3 sm:grid-cols-2">
        <div class="text-sm">
          <span class="text-(--text-muted)">{{ t('waitingList.name') }}:</span>
          <span class="ml-1 font-medium">{{ list.name }}</span>
        </div>
        <div class="text-sm">
          <span class="text-(--text-muted)">{{ t('waitingList.confirmInterval') }}:</span>
          <span class="ml-1 font-medium">{{ list.confirmIntervalDays ?? '-' }} {{ t('waitingList.days') }}</span>
        </div>
        <div class="text-sm sm:col-span-2">
          <span class="text-(--text-muted)">{{ t('waitingList.description') }}:</span>
          <span class="ml-1 font-medium">{{ list.description || '-' }}</span>
        </div>
        <div class="text-sm sm:col-span-2">
          <span class="text-(--text-muted)">{{ t('waitingList.scoringFormula') }}:</span>
          <span class="ml-1 font-medium font-mono text-xs">{{ list.scoringFormula || '-' }}</span>
        </div>
        <div class="text-sm">
          <span class="text-(--text-muted)">{{ t('waitingList.testingGroup') }}:</span>
          <span class="ml-1 font-medium">{{ groupName(list.testingGroupId) }}</span>
        </div>
        <div class="text-sm">
          <span class="text-(--text-muted)">{{ t('waitingList.joinGroup') }}:</span>
          <span class="ml-1 font-medium">{{ groupName(list.joinGroupId) }}</span>
        </div>
        <div class="text-sm">
          <span class="text-(--text-muted)">{{ t('waitingList.attendanceThreshold') }}:</span>
          <span class="ml-1 font-medium">{{ list.attendanceThreshold }}</span>
        </div>
        <div class="text-sm">
          <span class="text-(--text-muted)">{{ t('waitingList.isPublic') }}:</span>
          <span v-if="list.isPublic" class="ml-1 font-medium text-success">{{ t('common.yes') }}</span>
          <span v-else class="ml-1 font-medium text-(--text-muted)">{{ t('common.no') }}</span>
        </div>
      </div>
    </template>

    <template v-else>
      <OverviewEditFields
        v-model:name="editName"
        v-model:description="editDescription"
        v-model:scoring-formula="editScoringFormula"
        v-model:confirm-interval="editConfirmInterval"
        v-model:testing-group-id="editTestingGroupId"
        v-model:join-group-id="editJoinGroupId"
        v-model:attendance-threshold="editAttendanceThreshold"
        :fields="fields"
        :groups="groups"
      />
      <OverviewEditActions
        v-model:is-public="editIsPublic"
        :can-save="canSave"
        :save="saveEditing"
        @cancel="cancelEditing"
      />
    </template>
  </NeutralContainer>
</template>
