/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {PermissionNode, PermissionGrant} from '@/api/types'
import {data} from '@/api'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'

const props = defineProps<{
  allRoles: PermissionGrant[]
  modelValue: Set<number>
  hiddenPermissions?: Set<string>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Set<number>): void
}>()

const {t} = useI18n()

const hierarchy = ref<PermissionNode[]>([])
const loading = ref(true)

onMounted(async () => {
  hierarchy.value = await data.getPermissionHierarchy()
  loading.value = false
})

interface TreeNode {
  name: string
  children: TreeNode[]
}

const tree = computed<TreeNode[]>(() => {
  if (hierarchy.value.length === 0) return []

  const nodeMap = new Map<string, PermissionNode>()
  for (const n of hierarchy.value) nodeMap.set(n.name, n)

  function buildNode(name: string): TreeNode {
    const source = nodeMap.get(name)
    return {
      name,
      children: source?.children.map(buildNode) ?? [],
    }
  }

  const admin = nodeMap.get('STATION_ADMINISTRATOR')
  if (!admin) return []

  // USER is always implicit; additionally hide permissions granted by the user's type
  const hidden = new Set(['USER', ...(props.hiddenPermissions ?? [])])

  function filterHidden(nodes: TreeNode[]): TreeNode[] {
    return nodes
        .filter(n => !hidden.has(n.name))
        .map(n => ({...n, children: filterHidden(n.children)}))
  }

  return filterHidden(admin.children.map(buildNode))
})

// Map permission name -> PermissionGrant (db id)
const roleByName = computed(() => {
  const map = new Map<string, PermissionGrant>()
  for (const r of props.allRoles) map.set(r.permission, r)
  return map
})

function isSelected(name: string): boolean {
  const role = roleByName.value.get(name)
  return role ? props.modelValue.has(role.id) : false
}

function allChildNames(node: TreeNode): string[] {
  const result: string[] = []
  for (const child of node.children) {
    result.push(child.name)
    result.push(...allChildNames(child))
  }
  return result
}

function toggle(name: string, node: TreeNode) {
  const newSet = new Set(props.modelValue)
  const role = roleByName.value.get(name)
  if (!role) return

  if (newSet.has(role.id)) {
    newSet.delete(role.id)
    for (const childName of allChildNames(node)) {
      const childRole = roleByName.value.get(childName)
      if (childRole) newSet.delete(childRole.id)
    }
  } else {
    newSet.add(role.id)
    for (const childName of allChildNames(node)) {
      const childRole = roleByName.value.get(childName)
      if (childRole) newSet.add(childRole.id)
    }
  }
  emit('update:modelValue', newSet)
}

function toggleLeaf(name: string) {
  const newSet = new Set(props.modelValue)
  const role = roleByName.value.get(name)
  if (!role) return
  if (newSet.has(role.id)) newSet.delete(role.id)
  else newSet.add(role.id)
  emit('update:modelValue', newSet)
}

const expanded = ref<Set<string>>(new Set())

function toggleExpand(name: string) {
  const newSet = new Set(expanded.value)
  if (newSet.has(name)) newSet.delete(name)
  else newSet.add(name)
  expanded.value = newSet
}

function isExpanded(name: string): boolean {
  return expanded.value.has(name)
}

function hasAnyChildSelected(node: TreeNode): boolean {
  for (const child of node.children) {
    if (isSelected(child.name)) return true
    if (hasAnyChildSelected(child)) return true
  }
  return false
}

function selectedChildCount(node: TreeNode): number {
  let count = 0
  for (const child of node.children) {
    if (isSelected(child.name)) count++
    count += selectedChildCount(child)
  }
  return count
}

function totalChildCount(node: TreeNode): number {
  let count = node.children.length
  for (const child of node.children) {
    count += totalChildCount(child)
  }
  return count
}

const GROUP_ICONS: Record<string, string[]> = {
  LOGIN: ['fas', 'right-to-bracket'],
  ATTENDANCE_MANAGER: ['fas', 'clipboard-check'],
  INVENTORY_MANAGER: ['fas', 'boxes-stacked'],
  EVENT_MANAGER: ['fas', 'calendar-days'],
  MEMBER_MANAGER: ['fas', 'users'],
  WAITLIST_MANAGER: ['fas', 'clock'],
  NEWS_MANAGER: ['fas', 'newspaper'],
  POLL_MANAGER: ['fas', 'square-poll-vertical'],
  LOST_AND_FOUND_MANAGER: ['fas', 'magnifying-glass'],
  QUIZ_MANAGER: ['fas', 'graduation-cap'],
  BOARD_MANAGER: ['fas', 'table-columns'],
  KNOWLEDGE_MANAGER: ['fas', 'book'],
  STATION_MANAGER: ['fas', 'gear'],
  NEWS_FEDERATE: ['fas', 'share-nodes'],
}
</script>

<template>
  <div>
    <Spinner v-if="loading" size="sm" />

    <div v-else class="space-y-2">
      <template v-for="node in tree" :key="node.name">
        <div class="rounded-lg border border-(--border) overflow-hidden transition-shadow" :class="{'shadow-sm border-primary/30': isSelected(node.name) || hasAnyChildSelected(node)}">
          <!-- Group header -->
          <div
              class="flex items-center gap-3 px-3 py-2.5 cursor-pointer select-none transition-colors"
              :class="isSelected(node.name) ? 'bg-primary/5' : 'hover:bg-bg-light-accent/40 dark:hover:bg-bg-dark-accent/40'"
              @click="toggleExpand(node.name)"
          >
            <ToggleInput
                :model-value="isSelected(node.name)"
                @update:model-value="toggle(node.name, node)"
                @click.stop
            />
            <font-awesome-icon
                v-if="GROUP_ICONS[node.name]"
                :icon="GROUP_ICONS[node.name]"
                class="h-4 w-4 text-(--text-muted)"
            />
            <div class="flex-1 min-w-0">
              <div class="font-medium text-sm">{{ t(`permissions.${node.name}.label`) }}</div>
              <div class="text-xs text-(--text-muted) leading-tight">{{ t(`permissions.${node.name}.desc`) }}</div>
            </div>
            <span
                v-if="!isSelected(node.name) && hasAnyChildSelected(node)"
                class="text-[10px] text-primary font-medium whitespace-nowrap"
            >{{ selectedChildCount(node) }}/{{ totalChildCount(node) }}</span>
            <font-awesome-icon
                v-if="node.children.length > 0"
                :icon="['fas', isExpanded(node.name) ? 'chevron-up' : 'chevron-down']"
                class="h-3 w-3 text-(--text-muted) shrink-0"
            />
          </div>

          <!-- Children -->
          <div v-if="node.children.length > 0 && isExpanded(node.name)" class="border-t border-(--border) bg-bg-light-accent/20 dark:bg-bg-dark-accent/20">
            <template v-for="child in node.children" :key="child.name">
              <!-- Leaf permission -->
              <div
                  v-if="child.children.length === 0"
                  class="flex items-center gap-3 px-3 py-2 pl-10 transition-colors"
                  :class="isSelected(node.name) ? 'opacity-40' : 'hover:bg-bg-light-accent/40 dark:hover:bg-bg-dark-accent/40'"
              >
                <ToggleInput
                    :model-value="isSelected(child.name) || isSelected(node.name)"
                    :disabled="isSelected(node.name)"
                    @update:model-value="toggleLeaf(child.name)"
                />
                <div class="min-w-0">
                  <div class="text-sm">{{ t(`permissions.${child.name}.label`) }}</div>
                  <div class="text-xs text-(--text-muted) leading-tight">{{ t(`permissions.${child.name}.desc`) }}</div>
                </div>
              </div>

              <!-- Nested group -->
              <div v-else>
                <div
                    class="flex items-center gap-3 px-3 py-2 pl-10 cursor-pointer select-none transition-colors"
                    :class="isSelected(node.name) ? 'opacity-40' : 'hover:bg-bg-light-accent/40 dark:hover:bg-bg-dark-accent/40'"
                    @click="toggleExpand(child.name)"
                >
                  <ToggleInput
                      :model-value="isSelected(child.name) || isSelected(node.name)"
                      :disabled="isSelected(node.name)"
                      @update:model-value="toggle(child.name, child)"
                      @click.stop
                  />
                  <div class="flex-1 min-w-0">
                    <div class="text-sm font-medium">{{ t(`permissions.${child.name}.label`) }}</div>
                    <div class="text-xs text-(--text-muted) leading-tight">{{ t(`permissions.${child.name}.desc`) }}</div>
                  </div>
                  <font-awesome-icon
                      :icon="['fas', isExpanded(child.name) ? 'chevron-up' : 'chevron-down']"
                      class="h-3 w-3 text-(--text-muted) shrink-0"
                  />
                </div>
                <div v-if="isExpanded(child.name)">
                  <div
                      v-for="leaf in child.children"
                      :key="leaf.name"
                      class="flex items-center gap-3 px-3 py-2 pl-16 transition-colors"
                      :class="(isSelected(node.name) || isSelected(child.name)) ? 'opacity-40' : 'hover:bg-bg-light-accent/40 dark:hover:bg-bg-dark-accent/40'"
                  >
                    <ToggleInput
                        :model-value="isSelected(leaf.name) || isSelected(child.name) || isSelected(node.name)"
                        :disabled="isSelected(node.name) || isSelected(child.name)"
                        @update:model-value="toggleLeaf(leaf.name)"
                    />
                    <div class="min-w-0">
                      <div class="text-sm">{{ t(`permissions.${leaf.name}.label`) }}</div>
                      <div class="text-xs text-(--text-muted) leading-tight">{{ t(`permissions.${leaf.name}.desc`) }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>
