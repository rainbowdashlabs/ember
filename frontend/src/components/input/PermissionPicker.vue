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

  return filterHidden([buildNode('STATION_ADMINISTRATOR'), ...admin.children.map(buildNode)])
})

// Map permission name -> PermissionGrant (db id)
const roleByName = computed(() => {
  const map = new Map<string, PermissionGrant>()
  for (const r of props.allRoles) map.set(r.permission, r)
  return map
})

function isDirectlySelected(name: string): boolean {
  const role = roleByName.value.get(name)
  return role ? props.modelValue.has(role.id) : false
}

// Map: permission name -> name of the ancestor that implicitly grants it
const implicitlyGrantedBy = computed<Map<string, string>>(() => {
  const map = new Map<string, string>()
  function markChildren(node: TreeNode, grantedBy: string) {
    for (const child of node.children) {
      if (!map.has(child.name)) map.set(child.name, grantedBy)
      markChildren(child, grantedBy)
    }
  }
  function walk(nodes: TreeNode[]) {
    for (const node of nodes) {
      if (isDirectlySelected(node.name)) markChildren(node, node.name)
      walk(node.children)
    }
  }
  walk(tree.value)
  return map
})

function isEffectivelyEnabled(name: string): boolean {
  return isDirectlySelected(name) || implicitlyGrantedBy.value.has(name)
}

function isImplicit(name: string): boolean {
  return implicitlyGrantedBy.value.has(name)
}

function grantedByLabel(name: string): string {
  const grantedBy = implicitlyGrantedBy.value.get(name)
  if (!grantedBy) return ''
  return t(`permissions.${grantedBy}.label`)
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
  if (isImplicit(name)) return
  const newSet = new Set(props.modelValue)
  const role = roleByName.value.get(name)
  if (!role) return

  if (newSet.has(role.id)) {
    newSet.delete(role.id)
  } else {
    newSet.add(role.id)
    // Remove explicitly selected children (now implicit)
    for (const childName of allChildNames(node)) {
      const childRole = roleByName.value.get(childName)
      if (childRole) newSet.delete(childRole.id)
    }
  }
  emit('update:modelValue', newSet)
}

function toggleLeaf(name: string) {
  if (isImplicit(name)) return
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

interface FlatItem {
  name: string
  node: TreeNode
  chainDepth: number
}

// Flatten all descendants of a node, sorted by chain depth (longest first)
function flattenDescendants(node: TreeNode): FlatItem[] {
  const items: FlatItem[] = []
  const seen = new Set<string>()
  function walk(n: TreeNode) {
    for (const child of n.children) {
      if (!seen.has(child.name)) {
        seen.add(child.name)
        items.push({ name: child.name, node: child, chainDepth: countMaxDepth(child) })
      }
      walk(child)
    }
  }
  walk(node)
  items.sort((a, b) => b.chainDepth - a.chainDepth)
  return items
}

function countEnabledDescendants(node: TreeNode): number {
  const items = flattenDescendants(node)
  return items.filter(i => isEffectivelyEnabled(i.name)).length
}

function countTotalDescendants(node: TreeNode): number {
  return flattenDescendants(node).length
}

function countMaxDepth(node: TreeNode): number {
  if (node.children.length === 0) return 0
  return 1 + Math.max(...node.children.map(countMaxDepth))
}

const GROUP_ICONS: Record<string, string[]> = {
  STATION_ADMINISTRATOR: ['fas', 'user-shield'],
  LOGIN: ['fas', 'right-to-bracket'],
  ATTENDANCE_MANAGER: ['fas', 'clipboard-check'],
  INVENTORY_MANAGER: ['fas', 'boxes-stacked'],
  EVENT_MANAGER: ['fas', 'calendar-days'],
  MEMBER_MANAGER: ['fas', 'users'],
  WAITLIST_MANAGER: ['fas', 'clock'],
  NEWS_MANAGER: ['fas', 'newspaper'],
  POLL_MANAGER: ['fas', 'square-poll-vertical'],
  LOST_AND_FOUND_MANAGER: ['fas', 'magnifying-glass'],
  TEST_MANAGER: ['fas', 'graduation-cap'],
  PROTOCOL_MANAGER: ['fas', 'clipboard-list'],
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
      <div
          v-for="node in tree"
          :key="node.name"
          class="rounded-lg border border-(--border) overflow-hidden transition-shadow"
          :class="{'shadow-sm border-primary/30': isEffectivelyEnabled(node.name) || countEnabledDescendants(node) > 0}"
      >
        <!-- Top-level group header -->
        <div
            class="flex items-center gap-3 px-3 py-2.5 cursor-pointer select-none transition-colors"
            :class="[isEffectivelyEnabled(node.name) ? 'bg-primary/5' : 'hover:bg-bg-light-accent/40 dark:hover:bg-bg-dark-accent/40', isImplicit(node.name) ? 'opacity-60' : '']"
            @click="node.children.length > 0 && toggleExpand(node.name)"
        >
          <ToggleInput
              :model-value="isEffectivelyEnabled(node.name)"
              :disabled="isImplicit(node.name)"
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
            <div v-if="isImplicit(node.name)" class="text-[10px] text-primary italic mt-0.5">
              {{ t('permissions.grantedBy', { name: grantedByLabel(node.name) }) }}
            </div>
          </div>
          <span
              v-if="!isEffectivelyEnabled(node.name) && node.children.length > 0 && countEnabledDescendants(node) > 0"
              class="text-[10px] text-primary font-medium whitespace-nowrap"
          >{{ countEnabledDescendants(node) }}/{{ countTotalDescendants(node) }}</span>
          <font-awesome-icon
              v-if="node.children.length > 0"
              :icon="['fas', isExpanded(node.name) ? 'chevron-up' : 'chevron-down']"
              class="h-3 w-3 text-(--text-muted) shrink-0"
          />
        </div>

        <!-- Flat list of all descendants -->
        <div v-if="node.children.length > 0 && isExpanded(node.name)" class="border-t border-(--border)">
          <div
              v-for="item in flattenDescendants(node)"
              :key="item.name"
              class="flex items-center gap-3 px-3 py-2 pl-10 transition-colors"
              :class="isImplicit(item.name) ? 'opacity-60 bg-bg-light-accent/10 dark:bg-bg-dark-accent/10' : 'hover:bg-bg-light-accent/40 dark:hover:bg-bg-dark-accent/40'"
          >
            <ToggleInput
                :model-value="isEffectivelyEnabled(item.name)"
                :disabled="isImplicit(item.name)"
                @update:model-value="item.node.children.length > 0 ? toggle(item.name, item.node) : toggleLeaf(item.name)"
            />
            <div class="min-w-0">
              <div class="text-sm" :class="item.node.children.length > 0 ? 'font-medium' : ''">{{ t(`permissions.${item.name}.label`) }}</div>
              <div class="text-xs text-(--text-muted) leading-tight">{{ t(`permissions.${item.name}.desc`) }}</div>
              <div v-if="isImplicit(item.name)" class="text-[10px] text-primary italic mt-0.5">
                {{ t('permissions.grantedBy', { name: grantedByLabel(item.name) }) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
