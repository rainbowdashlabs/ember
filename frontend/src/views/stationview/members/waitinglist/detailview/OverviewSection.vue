/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FormulaInput from '@/components/input/FormulaInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { WaitingList, WaitingListField, MemberGroup, Role } from '@/api/types'
import { ref, computed } from 'vue'
import { waitingList as waitingListApi } from '@/api'

const props = defineProps<{
  list: WaitingList
  listId: number
  fields: WaitingListField[]
  groups: MemberGroup[]
  roles: Role[]
}>()

const emit = defineEmits<{
  updated: [list: WaitingList]
  error: [msg: string]
  success: [msg: string]
}>()

const { t } = useI18n()

const fieldInfos = computed(() => props.fields.map(f => ({ name: f.name, type: f.fieldType })))

const editing = ref(false)
const editName = ref('')
const editDescription = ref('')
const editScoringFormula = ref('')
const editConfirmInterval = ref(0)
const editTestingGroupId = ref<number | null>(null)
const editJoinGroupId = ref<number | null>(null)
const editJoinRoleId = ref<number | null>(null)
const editAttendanceThreshold = ref(5)
const saving = ref(false)

function startEditing() {
  editName.value = props.list.name
  editDescription.value = props.list.description ?? ''
  editScoringFormula.value = props.list.scoringFormula ?? ''
  editConfirmInterval.value = props.list.confirmIntervalDays ?? 0
  editTestingGroupId.value = props.list.testingGroupId ?? null
  editJoinGroupId.value = props.list.joinGroupId ?? null
  editJoinRoleId.value = props.list.joinRoleId ?? null
  editAttendanceThreshold.value = props.list.attendanceThreshold ?? 5
  editing.value = true
}

function cancelEditing() {
  editing.value = false
}

async function saveEditing() {
  if (!editName.value.trim()) return
  saving.value = true
  try {
    const updated = await waitingListApi.update(props.listId, {
      name: editName.value.trim(),
      description: editDescription.value.trim(),
      scoringFormula: editScoringFormula.value.trim() || undefined,
      confirmIntervalDays: editConfirmInterval.value || undefined,
      testingGroupId: editTestingGroupId.value,
      joinGroupId: editJoinGroupId.value,
      joinRoleId: editJoinRoleId.value,
      attendanceThreshold: editAttendanceThreshold.value,
    })
    editing.value = false
    emit('updated', updated)
    emit('success', t('waitingList.saved'))
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    emit('error', msg || t('common.error'))
  } finally {
    saving.value = false
  }
}

function groupName(groupId: number | null | undefined): string {
  if (!groupId) return '-'
  return props.groups.find(g => g.id === groupId)?.name ?? '-'
}

function roleName(roleId: number | null | undefined): string {
  if (!roleId) return '-'
  return props.roles.find(r => r.id === roleId)?.role ?? '-'
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('waitingList.overview') }}</SubHeader>
      <EditButton v-if="!editing" @click="startEditing" />
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
          <span class="text-(--text-muted)">{{ t('waitingList.joinRole') }}:</span>
          <span class="ml-1 font-medium">{{ roleName(list.joinRoleId) }}</span>
        </div>
        <div class="text-sm">
          <span class="text-(--text-muted)">{{ t('waitingList.attendanceThreshold') }}:</span>
          <span class="ml-1 font-medium">{{ list.attendanceThreshold }}</span>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="space-y-3">
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('waitingList.name') }}</label>
          <TextInput v-model="editName" />
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('waitingList.description') }}</label>
          <TextAreaInput v-model="editDescription" />
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('waitingList.scoringFormula') }}</label>
          <FormulaInput v-model="editScoringFormula" :placeholder="t('waitingList.scoringFormulaPlaceholder')" :fields="fieldInfos" />
          <p class="text-xs text-(--text-muted)">{{ t('waitingList.scoringFormulaHint') }}</p>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('waitingList.confirmInterval') }}</label>
          <NumberInput v-model="editConfirmInterval" />
          <p class="text-xs text-(--text-muted)">{{ t('waitingList.confirmIntervalHint') }}</p>
        </div>
        <div class="grid gap-3 sm:grid-cols-2">
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('waitingList.testingGroup') }}</label>
            <SelectInput :model-value="editTestingGroupId != null ? String(editTestingGroupId) : ''" @update:model-value="editTestingGroupId = $event ? Number($event) : null">
              <option value="">{{ t('waitingList.noGroup') }}</option>
              <option v-for="g in groups" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
            </SelectInput>
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('waitingList.joinGroup') }}</label>
            <SelectInput :model-value="editJoinGroupId != null ? String(editJoinGroupId) : ''" @update:model-value="editJoinGroupId = $event ? Number($event) : null">
              <option value="">{{ t('waitingList.noGroup') }}</option>
              <option v-for="g in groups" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
            </SelectInput>
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('waitingList.joinRole') }}</label>
            <SelectInput :model-value="editJoinRoleId != null ? String(editJoinRoleId) : ''" @update:model-value="editJoinRoleId = $event ? Number($event) : null">
              <option value="">{{ t('waitingList.noRole') }}</option>
              <option v-for="r in roles" :key="r.id" :value="String(r.id)">{{ r.role }}</option>
            </SelectInput>
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('waitingList.attendanceThreshold') }}</label>
            <NumberInput v-model="editAttendanceThreshold" />
          </div>
        </div>
        <div class="flex justify-end gap-2">
          <SecondaryButton @click="cancelEditing">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="saving || !editName.trim()" @click="saveEditing">
            {{ saving ? t('common.loading') : t('common.save') }}
          </PrimaryButton>
        </div>
      </div>
    </template>
  </NeutralContainer>
</template>
