/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, toRef} from 'vue'
import {useI18n} from 'vue-i18n'
import type {PermissionGrant} from '@/api/types'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {usePermissionTree, type PermissionScope} from '@/composables/usePermissionTree'
import {GROUP_ICONS} from '@/components/input/permissionpicker/groupIcons'

const props = defineProps<{
  allRoles: PermissionGrant[]
  modelValue: Set<number>
  lockedPermissions?: Map<string, string>
  /** Whose permissions these are. An association's are a different enum with its own root. */
  scope?: PermissionScope
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Set<number>): void
}>()

const {t} = useI18n()

const {
  loading,
  tree,
  isLocked,
  lockedLabel,
  isEffectivelyEnabled,
  isImplicit,
  isDisabled,
  grantedByLabel,
  toggle,
  toggleLeaf,
  toggleExpand,
  isExpanded,
  flattenDescendants,
  countEnabledDescendants,
  countTotalDescendants,
} = usePermissionTree(
    toRef(props, 'modelValue'),
    toRef(props, 'allRoles'),
    computed(() => props.lockedPermissions),
    next => emit('update:modelValue', next),
    props.scope ?? 'station',
)
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
            :class="[isEffectivelyEnabled(node.name) ? 'bg-primary/5' : 'hover:bg-bg-light-accent/40 dark:hover:bg-bg-dark-accent/40', isDisabled(node.name) ? 'opacity-60' : '']"
            @click="node.children.length > 0 && toggleExpand(node.name)"
        >
          <ToggleInput
              :model-value="isEffectivelyEnabled(node.name)"
              :disabled="isDisabled(node.name)"
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
            <div v-if="isLocked(node.name)" class="text-[10px] text-primary italic mt-0.5">
              {{ lockedLabel(node.name) }}
            </div>
            <div v-else-if="isImplicit(node.name)" class="text-[10px] text-primary italic mt-0.5">
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
              :class="isDisabled(item.name) ? 'opacity-60 bg-bg-light-accent/10 dark:bg-bg-dark-accent/10' : 'hover:bg-bg-light-accent/40 dark:hover:bg-bg-dark-accent/40'"
          >
            <ToggleInput
                :model-value="isEffectivelyEnabled(item.name)"
                :disabled="isDisabled(item.name)"
                @update:model-value="item.node.children.length > 0 ? toggle(item.name, item.node) : toggleLeaf(item.name)"
            />
            <div class="min-w-0">
              <div class="text-sm" :class="item.node.children.length > 0 ? 'font-medium' : ''">{{ t(`permissions.${item.name}.label`) }}</div>
              <div class="text-xs text-(--text-muted) leading-tight">{{ t(`permissions.${item.name}.desc`) }}</div>
              <div v-if="isLocked(item.name)" class="text-[10px] text-primary italic mt-0.5">
                {{ lockedLabel(item.name) }}
              </div>
              <div v-else-if="isImplicit(item.name)" class="text-[10px] text-primary italic mt-0.5">
                {{ t('permissions.grantedBy', { name: grantedByLabel(item.name) }) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
