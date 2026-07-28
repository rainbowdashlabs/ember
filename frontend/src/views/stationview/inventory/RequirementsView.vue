/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type { Inventory, InventoryRequirement } from '@/api/inventory'
import type { MemberGroup } from '@/api/types'
import { inventory, memberGroups } from '@/api'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useAsyncAction } from '@/composables/useAsyncAction'
import RequirementGroupCard from './requirementsview/RequirementGroupCard.vue'
import RequirementAddModal from './requirementsview/RequirementAddModal.vue'
import { userTypeFriendlyNames, type RequirementGroup } from './requirementsview/types'

const { t } = useI18n()

const inventories = ref<Inventory[]>([])
const requirements = ref<InventoryRequirement[]>([])
const allGroups = ref<MemberGroup[]>([])

const showAddModal = ref(false)
const addTargetType = ref<'userType' | 'group'>('userType')
const addUserType = ref('')
const addGroupId = ref('')
const addInventoryId = ref('')
const addQuantity = ref(1)

function userTypeName(userType: string): string {
  const labels: Record<string, string> = userTypeFriendlyNames
  return labels[userType] ?? userType
}

function groupName(groupId: number): string {
  return allGroups.value.find(g => g.id === groupId)?.name ?? `#${groupId}`
}

function inventoryName(invId: number): string {
  return inventories.value.find(i => i.id === invId)?.name ?? `#${invId}`
}

const grouped = computed((): RequirementGroup[] => {
  const userTypeMap = new Map<string, InventoryRequirement[]>()
  const groupMap = new Map<number, InventoryRequirement[]>()

  for (const req of requirements.value) {
    if (req.userType) {
      const list = userTypeMap.get(req.userType) ?? []
      list.push(req)
      userTypeMap.set(req.userType, list)
    } else if (req.groupId) {
      const list = groupMap.get(req.groupId) ?? []
      list.push(req)
      groupMap.set(req.groupId, list)
    }
  }

  const userTypeGroups: RequirementGroup[] = [...userTypeMap.entries()]
    .map(([key, items]) => ({ type: 'userType' as const, key, label: userTypeName(key), items: items.sort((a, b) => a.position - b.position) }))
    .sort((a, b) => a.label.localeCompare(b.label))

  const memberGroupGroups: RequirementGroup[] = [...groupMap.entries()]
    .map(([id, items]) => ({ type: 'group' as const, key: String(id), label: groupName(id), items: items.sort((a, b) => a.position - b.position) }))
    .sort((a, b) => a.label.localeCompare(b.label))

  return [...userTypeGroups, ...memberGroupGroups]
})

const {loading, error} = useAsyncLoader(async () => {
  const [invs, reqs, groups] = await Promise.all([
    inventory.listInventories(),
    inventory.listAllRequirements(),
    memberGroups.listGroups(),
  ])
  inventories.value = invs
  requirements.value = reqs
  allGroups.value = groups
})

function openAdd(preselect?: { type: 'userType' | 'group'; key: string }) {
  addTargetType.value = preselect?.type ?? 'userType'
  addUserType.value = preselect?.type === 'userType' ? preselect.key : ''
  addGroupId.value = preselect?.type === 'group' ? preselect.key : ''
  addInventoryId.value = ''
  addQuantity.value = 1
  showAddModal.value = true
}

const {running: saving, error: addError, run: submitAdd} = useAsyncAction(async () => {
  if (!addInventoryId.value) return
  if (addTargetType.value === 'userType' && !addUserType.value) return
  if (addTargetType.value === 'group' && !addGroupId.value) return

  error.value = ''
  await inventory.createRequirement({
    inventoryId: Number(addInventoryId.value),
    userType: addTargetType.value === 'userType' ? addUserType.value : undefined,
    groupId: addTargetType.value === 'group' ? Number(addGroupId.value) : undefined,
    quantity: addQuantity.value,
  })
  showAddModal.value = false
  requirements.value = await inventory.listAllRequirements()
}, {formatError: () => t('common.error')})

async function updateQuantity(req: InventoryRequirement, newQuantity: number) {
  if (newQuantity < 1) return
  try {
    await inventory.updateRequirement(req.id, { quantity: newQuantity })
    requirements.value = await inventory.listAllRequirements()
  } catch {
    error.value = t('common.error')
  }
}

async function removeRequirement(req: InventoryRequirement) {
  try {
    await inventory.deleteRequirement(req.id)
    requirements.value = await inventory.listAllRequirements()
  } catch {
    error.value = t('common.error')
  }
}

async function onReorder(group: RequirementGroup, fromIndex: number, toIndex: number) {
  const items = [...group.items]
  const [moved] = items.splice(fromIndex, 1)
  if (!moved) return
  items.splice(toIndex, 0, moved)
  try {
    for (const [i, item] of items.entries()) {
      await inventory.updateRequirementPosition(item.id, i)
    }
    requirements.value = await inventory.listAllRequirements()
  } catch {
    error.value = t('common.error')
  }
}
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-requirements.title')"
      :subtitle="t('pages.inventory-requirements.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error || addError" variant="error">{{ error || addError }}</Alert>

      <template v-if="!loading">
        <div class="flex items-center justify-end">
          <PrimaryButton :icon="['fas', 'plus']" @click="openAdd()">
            {{ t('inventory.requirements.add') }}
          </PrimaryButton>
        </div>

        <p class="text-sm text-(--text-muted)">{{ t('inventory.requirements.hint') }}</p>

        <EmptyState v-if="grouped.length === 0">{{ t('inventory.requirements.empty') }}</EmptyState>

        <div class="space-y-4">
          <RequirementGroupCard
            v-for="group in grouped"
            :key="`${group.type}-${group.key}`"
            :group="group"
            :inventory-name="inventoryName"
            @add-item="openAdd"
            @update-quantity="updateQuantity"
            @remove="removeRequirement"
            @reorder="onReorder"
          />
        </div>
      </template>

      <RequirementAddModal
        v-model:show="showAddModal"
        v-model:target-type="addTargetType"
        v-model:user-type="addUserType"
        v-model:group-id="addGroupId"
        v-model:inventory-id="addInventoryId"
        v-model:quantity="addQuantity"
        :inventories="inventories"
        :all-groups="allGroups"
        :saving="saving"
        @submit="submitAdd"
      />
    </div>
  </ViewContent>
</template>
