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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type { Inventory, InventoryRequirement } from '@/api/inventory'
import type { MemberGroup } from '@/api/types'
import { inventory, memberGroups } from '@/api'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useInventoryRoutes } from '@/composables/useInventoryRoutes'
import RequirementGroupCard from './requirementsview/RequirementGroupCard.vue'
import RequirementAddModal from './requirementsview/RequirementAddModal.vue'
import { userTypeFriendlyNames, type RequirementGroup } from './requirementsview/types'
import { clusterStationGroups } from '@/api'
import type { StationGroup } from '@/api/clusterStationGroups'
import { moveWithin } from '@/util/reorder'
import { describeFailure } from '@/util/failure'

const { t } = useI18n()
const routes = useInventoryRoutes()

/**
 * Whether the screen is the association's rather than a station's.
 *
 * <p>An association writes one requirement for many stations, so it may point it at a group of them. A
 * station writes for itself and has nothing to point at, which is why the picker is absent there rather
 * than empty.
 */
const props = defineProps<{
  stationScoped?: boolean
}>()

const stationGroups = ref<StationGroup[]>([])

const inventories = ref<Inventory[]>([])
const requirements = ref<InventoryRequirement[]>([])
const allGroups = ref<MemberGroup[]>([])

const showAddModal = ref(false)
const addTargetType = ref<'userType' | 'group'>('userType')
const addUserType = ref('')
const addGroupId = ref('')
const addInventoryId = ref('')
const addStationGroupId = ref('')
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

/**
 * What may be asked for: only an inventory holding one thing in many copies.
 *
 * "Everybody needs one Sonstiges" is not a sentence, so a drawer of different things is simply not
 * offered here rather than being offered and then refused. The full list stays as it is, because it
 * is also what puts a name on the requirements that already exist.
 */
const requirableInventories = computed(() => inventories.value.filter(i => i.homogeneous))

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

/**
 * What is required of whom, and the lists the requirements are written against.
 *
 * <p>Groups are the station's own, so an association has none and asking for them is refused rather
 * than answered empty.
 */
const {loading, failure} = useAsyncLoader(async () => {
  const [invs, reqs, groups] = await Promise.all([
    inventory.listInventories(),
    inventory.listAllRequirements(),
    routes.memberGroups ? memberGroups.listGroups() : Promise.resolve([]),
  ])
  inventories.value = invs
  requirements.value = reqs
  allGroups.value = groups
  stationGroups.value = props.stationScoped ? await clusterStationGroups.listGroups() : []
})

function openAdd(preselect?: { type: 'userType' | 'group'; key: string }) {
  addTargetType.value = preselect?.type ?? 'userType'
  addUserType.value = preselect?.type === 'userType' ? preselect.key : ''
  addGroupId.value = preselect?.type === 'group' ? preselect.key : ''
  addInventoryId.value = ''
  addStationGroupId.value = ''
  addQuantity.value = 1
  showAddModal.value = true
}

const {running: saving, failure: addFailure, run: submitAdd} = useAsyncAction(async () => {
  if (!addInventoryId.value) return
  if (addTargetType.value === 'userType' && !addUserType.value) return
  if (addTargetType.value === 'group' && !addGroupId.value) return

  failure.value = null
  await inventory.createRequirement({
    inventoryId: Number(addInventoryId.value),
    userType: addTargetType.value === 'userType' ? addUserType.value : undefined,
    groupId: addTargetType.value === 'group' ? Number(addGroupId.value) : undefined,
    stationGroupId: addStationGroupId.value ? Number(addStationGroupId.value) : undefined,
    quantity: addQuantity.value,
  })
  showAddModal.value = false
  await refreshAfterWrite()
})

/**
 * Reads the requirements again after one was written, and says a stale screen rather than a failed
 * change: the change is in by then, and a reader told otherwise makes the same change twice.
 */
async function refreshAfterWrite() {
  try {
    requirements.value = await inventory.listAllRequirements()
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
  }
}

async function updateQuantity(req: InventoryRequirement, newQuantity: number) {
  if (newQuantity < 1) return
  failure.value = null
  try {
    await inventory.updateRequirement(req.id, { quantity: newQuantity })
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  await refreshAfterWrite()
}

async function removeRequirement(req: InventoryRequirement) {
  failure.value = null
  try {
    await inventory.deleteRequirement(req.id)
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  await refreshAfterWrite()
}

/**
 * Writes the new order back one requirement at a time. A failure halfway leaves the order part
 * written, which is what the guidance to reload is for here.
 */
async function onReorder(group: RequirementGroup, fromIndex: number, toIndex: number) {
  const items = moveWithin(group.items, fromIndex, toIndex)
  failure.value = null
  try {
    for (const [i, item] of items.entries()) {
      await inventory.updateRequirementPosition(item.id, i)
    }
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  await refreshAfterWrite()
}
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-requirements.title')"
      :subtitle="t('pages.inventory-requirements.subtitle')"
  >
    <slot name="before"/>

    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <FailureAlert :failure="failure ?? addFailure"/>

      <template v-if="!loading">
        <div class="flex items-center justify-end">
          <PrimaryButton :icon="['fas', 'plus']" data-testid="requirement-add" @click="openAdd()">
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
          :station-groups="stationGroups"
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
        v-model:station-group-id="addStationGroupId"
        :inventories="requirableInventories"
        :all-groups="allGroups"
        :station-groups="stationGroups"
        :saving="saving"
        @submit="submitAdd"
      />
    </div>
  </ViewContent>
</template>
