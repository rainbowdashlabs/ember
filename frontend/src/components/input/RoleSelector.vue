/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {Role} from '@/api/types'
import {Roles} from '@/api/types'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {useBreakpoint} from '@/composables/useBreakpoint'

const {isMobile} = useBreakpoint()

const props = defineProps<{
  allRoles: Role[]
  modelValue: Set<number>
  allowedRoles?: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Set<number>): void
}>()

const {t} = useI18n()

const ASSIGNABLE_ROLE_NAMES = [
  Roles.MEMBER, Roles.GUARDIAN, Roles.TEAM, Roles.LOGIN,
  Roles.ATTENDANCE_MANAGER, Roles.ATTENDANCE_EXPORT_MANAGER,
  Roles.INVENTORY_MANAGER, Roles.EVENT_MANAGER,
  Roles.MEMBER_MANAGER, Roles.NEWS_MANAGER,
  Roles.POLL_MANAGER, Roles.LOST_AND_FOUND_MANAGER,
  Roles.MANAGER,
] as readonly string[]

const roleHierarchy: Record<string, string[]> = {
  [Roles.MANAGER]: [
    Roles.TEAM, Roles.ATTENDANCE_MANAGER, Roles.ATTENDANCE_EXPORT_MANAGER,
    Roles.INVENTORY_MANAGER, Roles.EVENT_MANAGER, Roles.MEMBER_MANAGER,
    Roles.NEWS_MANAGER, Roles.POLL_MANAGER, Roles.LOST_AND_FOUND_MANAGER,
  ],
  [Roles.TEAM]: [Roles.LOGIN, Roles.USER],
  [Roles.GUARDIAN]: [Roles.USER, Roles.LOGIN],
  [Roles.MEMBER]: [Roles.USER],
  [Roles.ATTENDANCE_MANAGER]: [Roles.TEAM],
  [Roles.ATTENDANCE_EXPORT_MANAGER]: [Roles.TEAM],
  [Roles.INVENTORY_MANAGER]: [Roles.TEAM],
  [Roles.EVENT_MANAGER]: [Roles.TEAM],
  [Roles.MEMBER_MANAGER]: [Roles.TEAM],
  [Roles.NEWS_MANAGER]: [Roles.TEAM],
  [Roles.POLL_MANAGER]: [Roles.TEAM],
  [Roles.LOST_AND_FOUND_MANAGER]: [Roles.TEAM],
}

const roleConflicts: Record<string, string[]> = {
  [Roles.MEMBER]: [
    Roles.TEAM, Roles.GUARDIAN, Roles.MANAGER,
    Roles.ATTENDANCE_MANAGER, Roles.ATTENDANCE_EXPORT_MANAGER,
    Roles.INVENTORY_MANAGER, Roles.EVENT_MANAGER,
    Roles.MEMBER_MANAGER, Roles.NEWS_MANAGER,
    Roles.POLL_MANAGER, Roles.LOST_AND_FOUND_MANAGER,
  ],
  [Roles.TEAM]: [Roles.MEMBER],
  [Roles.GUARDIAN]: [Roles.MEMBER],
  [Roles.MANAGER]: [Roles.MEMBER],
  [Roles.ATTENDANCE_MANAGER]: [Roles.MEMBER],
  [Roles.ATTENDANCE_EXPORT_MANAGER]: [Roles.MEMBER],
  [Roles.INVENTORY_MANAGER]: [Roles.MEMBER],
  [Roles.EVENT_MANAGER]: [Roles.MEMBER],
  [Roles.MEMBER_MANAGER]: [Roles.MEMBER],
  [Roles.NEWS_MANAGER]: [Roles.MEMBER],
  [Roles.POLL_MANAGER]: [Roles.MEMBER],
  [Roles.LOST_AND_FOUND_MANAGER]: [Roles.MEMBER],
}

const roleFriendlyNames: Record<string, string> = {
  LOGIN: 'Login',
  USER: 'Nutzer',
  MEMBER: 'Mitglied',
  TEAM: 'Team',
  GUARDIAN: 'Erziehungsberechtigter',
  ATTENDANCE_MANAGER: 'Anwesenheitsverwaltung',
  ATTENDANCE_EXPORT_MANAGER: 'Anwesenheitsexport',
  INVENTORY_MANAGER: 'Inventarverwaltung',
  EVENT_MANAGER: 'Terminverwaltung',
  MEMBER_MANAGER: 'Mitgliederverwaltung',
  NEWS_MANAGER: 'Neuigkeiten',
  POLL_MANAGER: 'Formularverwaltung',
  LOST_AND_FOUND_MANAGER: 'Fundbüro',
  MANAGER: 'Manager',
}

const visibleRoleNames = computed(() => {
  return props.allowedRoles ?? ASSIGNABLE_ROLE_NAMES as readonly string[]
})

const assignableRoles = computed(() => {
  return props.allRoles.filter(r => visibleRoleNames.value.includes(r.role))
})

function getAllChildren(roleName: string): string[] {
  const direct = roleHierarchy[roleName] ?? []
  const all: string[] = [...direct]
  for (const child of direct) all.push(...getAllChildren(child))
  return [...new Set(all)]
}

function getDirectChildren(roleName: string): string[] {
  return (roleHierarchy[roleName] ?? []).filter(r => ASSIGNABLE_ROLE_NAMES.includes(r))
}

function isRoleIncluded(roleName: string): boolean {
  for (const selectedId of props.modelValue) {
    const selected = props.allRoles.find(r => r.id === selectedId)
    if (!selected) continue
    if (getAllChildren(selected.role).includes(roleName)) return true
  }
  return false
}

function isRoleConflicting(roleName: string): boolean {
  for (const selectedId of props.modelValue) {
    const selected = props.allRoles.find(r => r.id === selectedId)
    if (!selected) continue
    if ((roleConflicts[selected.role] ?? []).includes(roleName)) return true
  }
  return false
}

function isRoleDisabled(role: Role): boolean {
  return isRoleIncluded(role.role) || isRoleConflicting(role.role)
}

function statusHint(role: Role): string {
  if (isRoleIncluded(role.role)) return t('memberEdit.roleIncluded')
  if (isRoleConflicting(role.role)) return t('memberEdit.roleConflicting')
  return ''
}

function toggleRole(role: Role) {
  if (isRoleDisabled(role)) return
  const newSet = new Set(props.modelValue)
  if (newSet.has(role.id)) newSet.delete(role.id)
  else newSet.add(role.id)
  emit('update:modelValue', newSet)
}

function isSelected(role: Role): boolean {
  return props.modelValue.has(role.id)
}
</script>

<template>
  <!-- Mobile card layout -->
  <div v-if="isMobile" class="space-y-2">
    <div
      v-for="role in assignableRoles"
      :key="role.id"
      class="flex items-center gap-3 rounded-lg px-3 py-2 border border-(--border)"
      :class="{ 'opacity-40': isRoleDisabled(role) && !isSelected(role) }"
    >
      <ToggleInput
        :model-value="isSelected(role) || isRoleIncluded(role.role)"
        :disabled="isRoleDisabled(role)"
        @update:model-value="toggleRole(role)"
      />
      <div class="min-w-0">
        <div class="font-medium text-sm">{{ roleFriendlyNames[role.role] ?? role.role }}</div>
        <div v-if="statusHint(role)" class="text-xs text-(--text-muted)">{{ statusHint(role) }}</div>
        <div v-if="getDirectChildren(role.role).length > 0" class="flex flex-wrap gap-1 mt-1">
          <span
            v-for="child in getDirectChildren(role.role)"
            :key="child"
            class="inline-block rounded-full bg-secondary/10 text-secondary px-2 py-0.5 text-xs"
          >{{ roleFriendlyNames[child] ?? child }}</span>
        </div>
      </div>
    </div>
  </div>

  <!-- Desktop table layout -->
  <div v-else class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <tr class="border-b border-(--border) text-left">
          <th class="py-2 pr-4 font-medium text-(--text-muted)">{{ t('memberEdit.role') }}</th>
          <th class="py-2 pr-4 font-medium text-(--text-muted)">{{ t('memberEdit.includes') }}</th>
          <th class="py-2 text-right font-medium text-(--text-muted)">{{ t('memberEdit.active') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="role in assignableRoles"
          :key="role.id"
          class="border-b border-(--border) last:border-0"
          :class="{
            'opacity-40': isRoleDisabled(role) && !isSelected(role),
          }"
        >
          <td class="py-2.5 pr-4">
            <div class="font-medium">{{ roleFriendlyNames[role.role] ?? role.role }}</div>
            <div v-if="statusHint(role)" class="text-xs text-(--text-muted)">{{ statusHint(role) }}</div>
          </td>
          <td class="py-2.5 pr-4">
            <div class="flex flex-wrap gap-1">
              <span
                v-for="child in getDirectChildren(role.role)"
                :key="child"
                class="inline-block rounded-full bg-secondary/10 text-secondary px-2 py-0.5 text-xs"
              >{{ roleFriendlyNames[child] ?? child }}</span>
            </div>
          </td>
          <td class="py-2.5 text-right">
            <ToggleInput
              :model-value="isSelected(role) || isRoleIncluded(role.role)"
              :disabled="isRoleDisabled(role)"
              @update:model-value="toggleRole(role)"
            />
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
