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
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { Inventory, InventoryRequirement, MemberGroup, Role } from '@/api/types'
import { inventory, memberGroups, stationMembers } from '@/api'

const { t } = useI18n()

const inventories = ref<Inventory[]>([])
const requirements = ref<InventoryRequirement[]>([])
const allRoles = ref<Role[]>([])
const allGroups = ref<MemberGroup[]>([])
const loading = ref(true)
const error = ref('')

// Add modal
const showAddModal = ref(false)
const addTargetType = ref<'role' | 'group'>('role')
const addRoleId = ref('')
const addGroupId = ref('')
const addInventoryId = ref('')
const addQuantity = ref(1)
const saving = ref(false)

const roleFriendlyNames: Record<string, string> = {
  LOGIN: 'Login',
  MEMBER: 'Mitglied',
  TEAM: 'Team',
  GUARDIAN: 'Erziehungsberechtigter',
  ATTENDENCE_MANAGEMENT: 'Anwesenheitsverwaltung',
  INVENTORY_MANAGEMENT: 'Inventarverwaltung',
  EVENT_MANAGEMENT: 'Terminverwaltung',
  MEMBER_MANAGEMENT: 'Mitgliederverwaltung',
  MANAGER: 'Manager',
  NEWS_MANAGEMENT: 'Neuigkeiten',
}

function roleName(roleId: number): string {
  const role = allRoles.value.find(r => r.id === roleId)
  if (!role) return `#${roleId}`
  return roleFriendlyNames[role.role] ?? role.role
}

function groupName(groupId: number): string {
  return allGroups.value.find(g => g.id === groupId)?.name ?? `#${groupId}`
}

function inventoryName(invId: number): string {
  return inventories.value.find(i => i.id === invId)?.name ?? `#${invId}`
}

interface RequirementGroup {
  type: 'role' | 'group'
  id: number
  label: string
  items: InventoryRequirement[]
}

const grouped = computed((): RequirementGroup[] => {
  const roleMap = new Map<number, InventoryRequirement[]>()
  const groupMap = new Map<number, InventoryRequirement[]>()

  for (const req of requirements.value) {
    if (req.roleId) {
      const list = roleMap.get(req.roleId) ?? []
      list.push(req)
      roleMap.set(req.roleId, list)
    } else if (req.groupId) {
      const list = groupMap.get(req.groupId) ?? []
      list.push(req)
      groupMap.set(req.groupId, list)
    }
  }

  const roleGroups: RequirementGroup[] = [...roleMap.entries()]
    .map(([id, items]) => ({ type: 'role' as const, id, label: roleName(id), items: items.sort((a, b) => a.position - b.position) }))
    .sort((a, b) => a.label.localeCompare(b.label))

  const memberGroupGroups: RequirementGroup[] = [...groupMap.entries()]
    .map(([id, items]) => ({ type: 'group' as const, id, label: groupName(id), items: items.sort((a, b) => a.position - b.position) }))
    .sort((a, b) => a.label.localeCompare(b.label))

  return [...roleGroups, ...memberGroupGroups]
})

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [invs, reqs, roles, groups] = await Promise.all([
      inventory.listInventories(),
      inventory.listAllRequirements(),
      stationMembers.listAllRoles(),
      memberGroups.listGroups(),
    ])
    inventories.value = invs
    requirements.value = reqs
    allRoles.value = roles
    allGroups.value = groups
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function openAdd(preselect?: { type: 'role' | 'group'; id: number }) {
  addTargetType.value = preselect?.type ?? 'role'
  addRoleId.value = preselect?.type === 'role' ? String(preselect.id) : ''
  addGroupId.value = preselect?.type === 'group' ? String(preselect.id) : ''
  addInventoryId.value = ''
  addQuantity.value = 1
  showAddModal.value = true
}

async function submitAdd() {
  if (!addInventoryId.value) return
  if (addTargetType.value === 'role' && !addRoleId.value) return
  if (addTargetType.value === 'group' && !addGroupId.value) return

  saving.value = true
  error.value = ''
  try {
    await inventory.createRequirement({
      inventoryId: Number(addInventoryId.value),
      roleId: addTargetType.value === 'role' ? Number(addRoleId.value) : undefined,
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
          <PrimaryButton @click="openAdd()">
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
            {{ t('inventory.requirements.add') }}
          </PrimaryButton>
        </div>

        <p class="text-sm text-(--text-muted)">{{ t('inventory.requirements.hint') }}</p>

        <div v-if="grouped.length === 0" class="text-center text-(--text-muted) py-8">
          {{ t('inventory.requirements.empty') }}
        </div>

        <div class="space-y-4">
          <NeutralContainer v-for="group in grouped" :key="`${group.type}-${group.id}`" class="space-y-3">
            <div class="flex items-center justify-between">
              <SubHeader>
                <span class="text-xs uppercase tracking-wide text-(--text-muted) mr-2">
                  {{ group.type === 'role' ? t('inventory.requirements.role') : t('inventory.requirements.group') }}
                </span>
                {{ group.label }}
              </SubHeader>
              <SecondaryButton @click="openAdd({ type: group.type, id: group.id })">
                <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                {{ t('inventory.requirements.addItem') }}
              </SecondaryButton>
            </div>

            <DragList :items="group.items" :key-fn="(r) => r.id" @reorder="(from, to) => onReorder(group, from, to)">
              <template #default="{ item: req }">
                <div class="grid grid-cols-[auto_1fr_6rem_2.5rem] gap-2 items-center px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 cursor-grab active:cursor-grabbing">
                  <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) h-3.5 w-3.5" />
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
            <label class="block text-sm font-medium">{{ t('inventory.requirements.targetType') }}</label>
            <SelectInput v-model="addTargetType">
              <option value="role">{{ t('inventory.requirements.byRole') }}</option>
              <option value="group">{{ t('inventory.requirements.byGroup') }}</option>
            </SelectInput>
          </div>

          <div v-if="addTargetType === 'role'" class="space-y-1">
            <label class="block text-sm font-medium">{{ t('inventory.requirements.role') }}</label>
            <SelectInput v-model="addRoleId">
              <option value="" disabled>{{ t('inventory.requirements.selectRole') }}</option>
              <option v-for="role in allRoles" :key="role.id" :value="String(role.id)">{{ roleFriendlyNames[role.role] ?? role.role }}</option>
            </SelectInput>
          </div>

          <div v-if="addTargetType === 'group'" class="space-y-1">
            <label class="block text-sm font-medium">{{ t('inventory.requirements.group') }}</label>
            <SelectInput v-model="addGroupId">
              <option value="" disabled>{{ t('inventory.requirements.selectGroup') }}</option>
              <option v-for="group in allGroups" :key="group.id" :value="String(group.id)">{{ group.name }}</option>
            </SelectInput>
          </div>

          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('inventory.requirements.inventory') }}</label>
            <SelectInput v-model="addInventoryId">
              <option value="" disabled>{{ t('inventory.requirements.selectInventory') }}</option>
              <option v-for="inv in inventories" :key="inv.id" :value="String(inv.id)">{{ inv.name }}</option>
            </SelectInput>
          </div>

          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('inventory.requirements.quantity') }}</label>
            <NumberInput v-model="addQuantity" :min="1" />
          </div>

          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showAddModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton
              :disabled="saving || !addInventoryId || (addTargetType === 'role' && !addRoleId) || (addTargetType === 'group' && !addGroupId)"
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
