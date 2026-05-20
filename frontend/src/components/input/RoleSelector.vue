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
  Roles.ATTENDENCE_MANAGEMENT, Roles.ATTENDENCE_EXPORT_MANAGER,
  Roles.INVENTORY_MANAGEMENT, Roles.EVENT_MANAGEMENT,
  Roles.MEMBER_MANAGEMENT, Roles.NEWS_MANAGEMENT,
  Roles.POLL_MANAGEMENT, Roles.LOST_AND_FOUND_MANAGEMENT,
  Roles.MANAGER,
] as readonly string[]

const roleHierarchy: Record<string, string[]> = {
  [Roles.MANAGER]: [
    Roles.TEAM, Roles.ATTENDENCE_MANAGEMENT, Roles.ATTENDENCE_EXPORT_MANAGER,
    Roles.INVENTORY_MANAGEMENT, Roles.EVENT_MANAGEMENT, Roles.MEMBER_MANAGEMENT,
    Roles.NEWS_MANAGEMENT, Roles.POLL_MANAGEMENT, Roles.LOST_AND_FOUND_MANAGEMENT,
  ],
  [Roles.TEAM]: [Roles.LOGIN, Roles.USER],
  [Roles.GUARDIAN]: [Roles.USER, Roles.LOGIN],
  [Roles.MEMBER]: [Roles.USER],
  [Roles.ATTENDENCE_MANAGEMENT]: [Roles.TEAM],
  [Roles.ATTENDENCE_EXPORT_MANAGER]: [Roles.TEAM],
  [Roles.INVENTORY_MANAGEMENT]: [Roles.TEAM],
  [Roles.EVENT_MANAGEMENT]: [Roles.TEAM],
  [Roles.MEMBER_MANAGEMENT]: [Roles.TEAM],
  [Roles.NEWS_MANAGEMENT]: [Roles.TEAM],
  [Roles.POLL_MANAGEMENT]: [Roles.TEAM],
  [Roles.LOST_AND_FOUND_MANAGEMENT]: [Roles.TEAM],
}

const roleConflicts: Record<string, string[]> = {
  [Roles.MEMBER]: [
    Roles.TEAM, Roles.GUARDIAN, Roles.MANAGER,
    Roles.ATTENDENCE_MANAGEMENT, Roles.ATTENDENCE_EXPORT_MANAGER,
    Roles.INVENTORY_MANAGEMENT, Roles.EVENT_MANAGEMENT,
    Roles.MEMBER_MANAGEMENT, Roles.NEWS_MANAGEMENT,
    Roles.POLL_MANAGEMENT, Roles.LOST_AND_FOUND_MANAGEMENT,
  ],
  [Roles.TEAM]: [Roles.MEMBER],
  [Roles.GUARDIAN]: [Roles.MEMBER],
  [Roles.MANAGER]: [Roles.MEMBER],
  [Roles.ATTENDENCE_MANAGEMENT]: [Roles.MEMBER],
  [Roles.ATTENDENCE_EXPORT_MANAGER]: [Roles.MEMBER],
  [Roles.INVENTORY_MANAGEMENT]: [Roles.MEMBER],
  [Roles.EVENT_MANAGEMENT]: [Roles.MEMBER],
  [Roles.MEMBER_MANAGEMENT]: [Roles.MEMBER],
  [Roles.NEWS_MANAGEMENT]: [Roles.MEMBER],
  [Roles.POLL_MANAGEMENT]: [Roles.MEMBER],
  [Roles.LOST_AND_FOUND_MANAGEMENT]: [Roles.MEMBER],
}

const roleFriendlyNames: Record<string, string> = {
  LOGIN: 'Login',
  USER: 'Nutzer',
  MEMBER: 'Mitglied',
  TEAM: 'Team',
  GUARDIAN: 'Erziehungsberechtigter',
  ATTENDENCE_MANAGEMENT: 'Anwesenheitsverwaltung',
  ATTENDENCE_EXPORT_MANAGER: 'Anwesenheitsexport',
  INVENTORY_MANAGEMENT: 'Inventarverwaltung',
  EVENT_MANAGEMENT: 'Terminverwaltung',
  MEMBER_MANAGEMENT: 'Mitgliederverwaltung',
  NEWS_MANAGEMENT: 'Neuigkeiten',
  POLL_MANAGEMENT: 'Formularverwaltung',
  LOST_AND_FOUND_MANAGEMENT: 'Fundbüro',
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
  <div class="overflow-x-auto">
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
