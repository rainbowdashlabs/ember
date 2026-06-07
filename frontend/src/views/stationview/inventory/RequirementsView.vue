/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import DragList from '@/components/input/DragList.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { Inventory, InventoryRequirement, MemberGroup } from '@/api/types'
import { StationUserType } from '@/api/types'
import { inventory, memberGroups } from '@/api'
import MutedIcon from '@/components/display/MutedIcon.vue'

const { t } = useI18n()

const inventories = ref<Inventory[]>([])
const requirements = ref<InventoryRequirement[]>([])
const allGroups = ref<MemberGroup[]>([])
const loading = ref(true)
const error = ref('')

// Add modal
const showAddModal = ref(false)
const addTargetType = ref<'userType' | 'group'>('userType')
const addUserType = ref('')
const addGroupId = ref('')
const addInventoryId = ref('')
const addQuantity = ref(1)
const saving = ref(false)

const userTypeFriendlyNames: Record<string, string> = {
  TRIAL: 'Probe',
  MEMBER: 'Mitglied',
  TEAM: 'Team',
  GUARDIAN: 'Erziehungsberechtigter',
  MANAGER: 'Manager',
}

function userTypeName(userType: string): string {
  return userTypeFriendlyNames[userType] ?? userType
}

function groupName(groupId: number): string {
  return allGroups.value.find(g => g.id === groupId)?.name ?? `#${groupId}`
}

function inventoryName(invId: number): string {
  return inventories.value.find(i => i.id === invId)?.name ?? `#${invId}`
}

interface RequirementGroup {
  type: 'userType' | 'group'
  key: string
  label: string
  items: InventoryRequirement[]
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

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [invs, reqs, groups] = await Promise.all([
      inventory.listInventories(),
      inventory.listAllRequirements(),
      memberGroups.listGroups(),
    ])
    inventories.value = invs
    requirements.value = reqs
    allGroups.value = groups
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function openAdd(preselect?: { type: 'userType' | 'group'; key: string }) {
  addTargetType.value = preselect?.type ?? 'userType'
  addUserType.value = preselect?.type === 'userType' ? preselect.key : ''
  addGroupId.value = preselect?.type === 'group' ? preselect.key : ''
  addInventoryId.value = ''
  addQuantity.value = 1
  showAddModal.value = true
}

async function submitAdd() {
  if (!addInventoryId.value) return
  if (addTargetType.value === 'userType' && !addUserType.value) return
  if (addTargetType.value === 'group' && !addGroupId.value) return

  saving.value = true
  error.value = ''
  try {
    await inventory.createRequirement({
      inventoryId: Number(addInventoryId.value),
      userType: addTargetType.value === 'userType' ? addUserType.value : undefined,
      groupId: addTargetType.value === 'group' ? Number(addGroupId.value) : undefined,
      quantity: addQuantity.value,
    })
    showAddModal.value = false
    requirements.value = await inventory.listAllRequirements()
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

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
  items.splice(toIndex, 0, moved)
  try {
    for (let i = 0; i < items.length; i++) {
      await inventory.updateRequirementPosition(items[i].id, i)
    }
    requirements.value = await inventory.listAllRequirements()
  } catch {
    error.value = t('common.error')
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <div class="flex items-center justify-between">
          <SectionHeader>{{ t('inventory.requirements.title') }}</SectionHeader>
          <PrimaryButton :icon="['fas', 'plus']" @click="openAdd()">
            {{ t('inventory.requirements.add') }}
          </PrimaryButton>
        </div>

        <p class="text-sm text-(--text-muted)">{{ t('inventory.requirements.hint') }}</p>

        <EmptyState v-if="grouped.length === 0">{{ t('inventory.requirements.empty') }}</EmptyState>

        <div class="space-y-4">
          <NeutralContainer v-for="group in grouped" :key="`${group.type}-${group.key}`" class="space-y-3">
            <div class="flex items-center justify-between">
              <SubHeader>
                <span class="text-xs uppercase tracking-wide text-(--text-muted) mr-2">
                  {{ group.type === 'userType' ? t('inventory.requirements.userType') : t('inventory.requirements.group') }}
                </span>
                {{ group.label }}
              </SubHeader>
              <SecondaryButton :icon="['fas', 'plus']" @click="openAdd({ type: group.type, key: group.key })">
                {{ t('inventory.requirements.addItem') }}
              </SecondaryButton>
            </div>

            <DragList :items="group.items" :key-fn="(r) => r.id" @reorder="(from, to) => onReorder(group, from, to)">
              <template #default="{ item: req }">
                <div class="grid grid-cols-[auto_1fr_6rem_2.5rem] gap-2 items-center px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 cursor-grab active:cursor-grabbing">
                  <MutedIcon size="md" :icon="['fas', 'grip-vertical']" />
                  <div class="text-sm">{{ inventoryName(req.inventoryId) }}</div>
                  <NumberInput :model-value="req.quantity" :min="1" @update:model-value="updateQuantity(req, $event as number)" />
                  <DeleteButton @click="removeRequirement(req)" />
                </div>
              </template>
            </DragList>
          </NeutralContainer>
        </div>
      </template>

      <!-- Add modal -->
      <Modal v-model="showAddModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('inventory.requirements.add') }}</SectionHeader>

          <div class="space-y-1">
            <FieldLabel>{{ t('inventory.requirements.targetType') }}</FieldLabel>
            <SelectInput v-model="addTargetType">
              <option value="userType">{{ t('inventory.requirements.byUserType') }}</option>
              <option value="group">{{ t('inventory.requirements.byGroup') }}</option>
            </SelectInput>
          </div>

          <div v-if="addTargetType === 'userType'" class="space-y-1">
            <FieldLabel>{{ t('inventory.requirements.userType') }}</FieldLabel>
            <SelectInput v-model="addUserType">
              <option value="" disabled>{{ t('inventory.requirements.selectUserType') }}</option>
              <option v-for="(value, key) in StationUserType" :key="key" :value="value">{{ userTypeFriendlyNames[value] ?? value }}</option>
            </SelectInput>
          </div>

          <div v-if="addTargetType === 'group'" class="space-y-1">
            <FieldLabel>{{ t('inventory.requirements.group') }}</FieldLabel>
            <SelectInput v-model="addGroupId">
              <option value="" disabled>{{ t('inventory.requirements.selectGroup') }}</option>
              <option v-for="group in allGroups" :key="group.id" :value="String(group.id)">{{ group.name }}</option>
            </SelectInput>
          </div>

          <div class="space-y-1">
            <FieldLabel>{{ t('inventory.requirements.inventory') }}</FieldLabel>
            <SelectInput v-model="addInventoryId">
              <option value="" disabled>{{ t('inventory.requirements.selectInventory') }}</option>
              <option v-for="inv in inventories" :key="inv.id" :value="String(inv.id)">{{ inv.name }}</option>
            </SelectInput>
          </div>

          <div class="space-y-1">
            <FieldLabel>{{ t('inventory.requirements.quantity') }}</FieldLabel>
            <NumberInput v-model="addQuantity" :min="1" />
          </div>

          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showAddModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton
              :disabled="saving || !addInventoryId || (addTargetType === 'userType' && !addUserType) || (addTargetType === 'group' && !addGroupId)"
              @click="submitAdd"
            >
              {{ saving ? t('common.loading') : t('common.save') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
