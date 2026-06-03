/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {PermissionGrant} from '@/api/types'
import {StationUserType, StationPermission} from '@/api/types'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {useBreakpoint} from '@/composables/useBreakpoint'

const {isMobile} = useBreakpoint()

const props = defineProps<{
  allRoles: PermissionGrant[]
  modelValue: Set<number>
  allowedRoles?: string[]
  userType?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Set<number>): void
  (e: 'update:userType', value: string): void
}>()

const {t} = useI18n()

const USER_TYPE_OPTIONS = [
  { value: StationUserType.MEMBER, label: 'Mitglied' },
  { value: StationUserType.GUARDIAN, label: 'Erziehungsberechtigter' },
  { value: StationUserType.TEAM, label: 'Team' },
  { value: StationUserType.MANAGER, label: 'Manager' },
  { value: StationUserType.TRIAL, label: 'Probe' },
] as const

const PERMISSION_NAMES: Record<string, string> = {
  [StationPermission.LOGIN]: 'Login',
  [StationPermission.USER]: 'Nutzer',
  [StationPermission.ATTENDANCE_MANAGER]: 'Anwesenheitsverwaltung',
  [StationPermission.ATTENDANCE_EXPORT]: 'Anwesenheitsexport',
  [StationPermission.INVENTORY_MANAGER]: 'Inventarverwaltung',
  [StationPermission.EVENT_MANAGER]: 'Terminverwaltung',
  [StationPermission.MEMBER_MANAGER]: 'Mitgliederverwaltung',
  [StationPermission.NEWS_MANAGER]: 'Neuigkeiten',
  [StationPermission.POLL_MANAGER]: 'Formularverwaltung',
  [StationPermission.LOST_AND_FOUND_MANAGER]: 'Fundbüro',
  [StationPermission.STATION_ADMINISTRATOR]: 'Manager',
  [StationPermission.MEMBER_GUARDIAN]: 'Erziehungsberechtigter',
}

const ASSIGNABLE_ROLE_NAMES = [
  StationPermission.ATTENDANCE_MANAGER, StationPermission.ATTENDANCE_EXPORT,
  StationPermission.INVENTORY_MANAGER, StationPermission.EVENT_MANAGER,
  StationPermission.MEMBER_MANAGER, StationPermission.NEWS_MANAGER,
  StationPermission.POLL_MANAGER, StationPermission.LOST_AND_FOUND_MANAGER,
  StationPermission.STATION_ADMINISTRATOR,
] as readonly string[]

const visibleRoleNames = computed(() => {
  return props.allowedRoles ?? ASSIGNABLE_ROLE_NAMES as readonly string[]
})

const assignableRoles = computed(() => {
  return props.allRoles.filter(r => visibleRoleNames.value.includes(r.permission))
})

function toggleRole(role: PermissionGrant) {
  const newSet = new Set(props.modelValue)
  if (newSet.has(role.id)) newSet.delete(role.id)
  else newSet.add(role.id)
  emit('update:modelValue', newSet)
}

function isSelected(role: PermissionGrant): boolean {
  return props.modelValue.has(role.id)
}

function onUserTypeChange(value: string | undefined) {
  if (value) emit('update:userType', value)
}
</script>

<template>
  <!-- User Type selector -->
  <div v-if="userType !== undefined" class="mb-4">
    <label class="block text-sm font-medium text-(--text-muted) mb-1">{{ t('memberEdit.userType') }}</label>
    <SelectInput :model-value="userType" @update:model-value="onUserTypeChange">
      <option v-for="opt in USER_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
    </SelectInput>
  </div>

  <!-- Permissions -->
  <!-- Mobile card layout -->
  <div v-if="isMobile" class="space-y-2">
    <div
      v-for="role in assignableRoles"
      :key="role.id"
      class="flex items-center gap-3 rounded-lg px-3 py-2 border border-(--border)"
    >
      <ToggleInput
        :model-value="isSelected(role)"
        @update:model-value="toggleRole(role)"
      />
      <div class="min-w-0">
        <div class="font-medium text-sm">{{ PERMISSION_NAMES[role.permission] ?? role.permission }}</div>
      </div>
    </div>
  </div>

  <!-- Desktop table layout -->
  <div v-else class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <tr class="border-b border-(--border) text-left">
          <th class="py-2 pr-4 font-medium text-(--text-muted)">{{ t('memberEdit.role') }}</th>
          <th class="py-2 text-right font-medium text-(--text-muted)">{{ t('memberEdit.active') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="role in assignableRoles"
          :key="role.id"
          class="border-b border-(--border) last:border-0"
        >
          <td class="py-2.5 pr-4">
            <div class="font-medium">{{ PERMISSION_NAMES[role.permission] ?? role.permission }}</div>
          </td>
          <td class="py-2.5 text-right">
            <ToggleInput
              :model-value="isSelected(role)"
              @update:model-value="toggleRole(role)"
            />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
