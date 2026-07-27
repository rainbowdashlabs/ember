/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import ItemModals from './ItemModals.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {InventoryDetail, InventoryItem, StationMember} from '@/api/types'
import {InventoryTypes, ItemSource} from '@/api/types'
import {inventory} from '@/api'
import {useBreakpoint} from '@/composables/useBreakpoint'
import {formatDate} from '@/util/format'

const {isMobile} = useBreakpoint()
const {t} = useI18n()

const props = defineProps<{
  detail: InventoryDetail
  items: InventoryItem[]
  members: StationMember[]
}>()

const emit = defineEmits<{
  itemsChanged: []
  error: [message: string]
}>()

const modals = ref<InstanceType<typeof ItemModals> | null>(null)

const itemSearch = ref('')
const filteredItems = computed(() => {
  const query = itemSearch.value.trim().toLowerCase()
  if (!query) return props.items
  return props.items.filter(item => {
    const name = (item.name ?? '').toLowerCase()
    const internalId = (item.internalId ?? '').toLowerCase()
    const memberName = getMemberName(item.assignedTo).toLowerCase()
    const sizeLabel = getSizeLabel(item.sizeId).toLowerCase()
    return name.includes(query) || internalId.includes(query) || memberName.includes(query) || sizeLabel.includes(query)
  })
})

function getMemberName(memberId: number | null | undefined): string {
  if (!memberId) return ''
  const m = props.members.find(mem => mem.id === memberId)
  return m ? (m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`) : `#${memberId}`
}

function getMemberIdentity(memberId: number | null | undefined) {
  if (!memberId) return null
  return props.members.find(mem => mem.id === memberId)?.identity ?? null
}

function getSizeLabel(sizeId: number | null | undefined): string {
  if (!sizeId || !props.detail.sizes) return ''
  return props.detail.sizes.find(s => s.id === sizeId)?.label ?? ''
}

async function unassignItem(item: InventoryItem) {
  try {
    if (props.detail.inventoryType === InventoryTypes.EXTERNAL) {
      await inventory.deleteItem(item.id)
    } else {
      await inventory.assignItem(item.id, {memberId: null, memberName: ''})
    }
    emit('itemsChanged')
  } catch {
    emit('error', t('common.error'))
  }
}

async function doMarkLost(item: InventoryItem) {
  try {
    await inventory.markLost(item.id)
    emit('itemsChanged')
  } catch {
    emit('error', t('common.error'))
  }
}

async function doMarkFound(item: InventoryItem) {
  try {
    await inventory.markFound(item.id)
    emit('itemsChanged')
  } catch {
    emit('error', t('common.error'))
  }
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('inventory.edit.itemsTitle') }}</SubHeader>
      <div class="flex items-center gap-2">
        <PrimaryButton v-if="detail.inventoryType === InventoryTypes.EXTERNAL || detail.inventoryType === InventoryTypes.MIXED"
                       :icon="['fas', 'user-plus']" @click="modals?.openQuickAssign()">
          {{ t('inventory.edit.quickAssign') }}
        </PrimaryButton>
        <PrimaryButton v-if="detail.inventoryType !== InventoryTypes.EXTERNAL"
                       :icon="['fas', 'plus']" @click="modals?.openAdd()">
          {{ t('inventory.edit.addItem') }}
        </PrimaryButton>
      </div>
    </div>

    <p v-if="detail.inventoryType === InventoryTypes.EXTERNAL" class="text-xs text-(--text-muted)">
      {{ t('inventory.edit.externalItemsHint') }}
    </p>

    <TextInput v-if="items.length > 0" v-model="itemSearch" :placeholder="t('inventory.edit.searchItems')"/>

    <div v-if="items.length === 0" class="text-center text-(--text-muted) py-4 text-sm">
      {{ t('inventory.edit.noItems') }}
    </div>

    <div v-if="filteredItems.length > 0 && isMobile" class="space-y-2">
      <NeutralContainer v-for="item in filteredItems" :key="item.id" :class="item.lostAt ? 'opacity-60' : ''">
        <div class="flex items-start justify-between gap-2">
          <div class="min-w-0">
            <div class="font-medium text-sm">{{ item.name }}</div>
            <span v-if="item.lostAt" class="text-xs text-error">{{ t('inventory.edit.lost') }} ({{ formatDate(item.lostAt) }})</span>
          </div>
          <span v-if="item.internalId" class="text-xs text-(--text-muted) shrink-0">{{ item.internalId }}</span>
        </div>
        <div class="grid grid-cols-2 gap-x-4 gap-y-1 mt-2 text-xs">
          <div v-if="detail.hasSizes && getSizeLabel(item.sizeId)">
            <div class="text-(--text-muted)">{{ t('inventory.edit.colSize') }}</div>
            <div>{{ getSizeLabel(item.sizeId) }}</div>
          </div>
          <div v-if="detail.inventoryType === InventoryTypes.MIXED">
            <div class="text-(--text-muted)">{{ t('inventory.edit.colSource') }}</div>
            <div>
              <PrimaryBadge v-if="item.itemSource === ItemSource.INTERNAL">{{ t('inventory.edit.sourceInternal') }}</PrimaryBadge>
              <SecondaryBadge v-else-if="item.itemSource === ItemSource.EXTERNAL">{{ t('inventory.edit.sourceExternal') }}</SecondaryBadge>
              <span v-else>&#x2013;</span>
            </div>
          </div>
          <div>
            <div class="text-(--text-muted)">{{ t('inventory.edit.colAssigned') }}</div>
            <div v-if="item.assignedTo" class="font-medium"><MemberName :identity="getMemberIdentity(item.assignedTo)"/></div>
            <div v-else class="text-(--text-muted)">&#x2013;</div>
          </div>
        </div>
        <div class="flex items-center gap-1 mt-2 pt-2 border-t border-bg-light-accent/50 dark:border-bg-dark-accent/50">
          <IconButton v-if="!item.lostAt" :icon="['fas', 'user']" :label="item.assignedTo ? t('inventory.edit.reassign') : t('inventory.edit.assign')" class="text-primary hover:bg-primary/15" @click="modals?.openAssign(item)"/>
          <IconButton v-if="item.assignedTo && !item.lostAt" :icon="['fas', 'right-from-bracket']" :label="t('inventory.edit.unassign')" class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent" @click="unassignItem(item)"/>
          <IconButton v-if="!item.lostAt" :icon="['fas', 'triangle-exclamation']" :label="t('inventory.edit.markLost')" class="text-error hover:bg-error/15" @click="doMarkLost(item)"/>
          <IconButton v-if="item.lostAt" :icon="['fas', 'check']" :label="t('inventory.edit.markFound')" class="text-success hover:bg-success/15" @click="doMarkFound(item)"/>
          <IconButton :icon="['fas', 'clock-rotate-left']" :label="t('inventory.edit.historyTitle')" class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent" @click="modals?.openHistory(item)"/>
          <EditButton @click="modals?.openEdit(item)"/>
          <DeleteButton @click="modals?.requestDelete(item)"/>
        </div>
      </NeutralContainer>
    </div>

    <div v-if="filteredItems.length > 0 && !isMobile" class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <THead>
            <Th>{{ t('inventory.edit.colName') }}</Th>
            <Th>{{ t('inventory.edit.colId') }}</Th>
            <Th v-if="detail.hasSizes">{{ t('inventory.edit.colSize') }}</Th>
            <Th v-if="detail.inventoryType === InventoryTypes.MIXED">{{ t('inventory.edit.colSource') }}</Th>
            <Th>{{ t('inventory.edit.colAssigned') }}</Th>
            <th class="px-3 py-2"></th>
          </THead>
        </thead>
        <tbody>
          <TRow v-for="item in filteredItems" :key="item.id" :class="item.lostAt ? 'opacity-60' : ''">
            <Td class="font-medium">
              {{ item.name }}
              <span v-if="item.lostAt" class="ml-2 text-xs text-error font-normal">{{ t('inventory.edit.lost') }} ({{ formatDate(item.lostAt) }})</span>
            </Td>
            <Td muted>{{ item.internalId || '\u2013' }}</Td>
            <Td v-if="detail.hasSizes" muted>{{ getSizeLabel(item.sizeId) || '\u2013' }}</Td>
            <Td v-if="detail.inventoryType === InventoryTypes.MIXED">
              <PrimaryBadge v-if="item.itemSource === ItemSource.INTERNAL">{{ t('inventory.edit.sourceInternal') }}</PrimaryBadge>
              <SecondaryBadge v-else-if="item.itemSource === ItemSource.EXTERNAL">{{ t('inventory.edit.sourceExternal') }}</SecondaryBadge>
              <span v-else class="text-(--text-muted)">&#x2013;</span>
            </Td>
            <Td>
              <MemberName v-if="item.assignedTo" :identity="getMemberIdentity(item.assignedTo)"/>
              <span v-else class="text-(--text-muted)">&#x2013;</span>
            </Td>
            <Td align="right">
              <div class="flex items-center justify-end gap-0.5">
                <IconButton v-if="!item.lostAt" :icon="['fas', 'user']" :label="item.assignedTo ? t('inventory.edit.reassign') : t('inventory.edit.assign')" class="text-primary hover:bg-primary/15" @click="modals?.openAssign(item)"/>
                <IconButton v-if="item.assignedTo && !item.lostAt" :icon="['fas', 'right-from-bracket']" :label="t('inventory.edit.unassign')" class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent" @click="unassignItem(item)"/>
                <IconButton v-if="!item.lostAt" :icon="['fas', 'triangle-exclamation']" :label="t('inventory.edit.markLost')" class="text-error hover:bg-error/15" @click="doMarkLost(item)"/>
                <IconButton v-if="item.lostAt" :icon="['fas', 'check']" :label="t('inventory.edit.markFound')" class="text-success hover:bg-success/15" @click="doMarkFound(item)"/>
                <IconButton :icon="['fas', 'clock-rotate-left']" :label="t('inventory.edit.historyTitle')" class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent" @click="modals?.openHistory(item)"/>
                <EditButton @click="modals?.openEdit(item)"/>
                <DeleteButton @click="modals?.requestDelete(item)"/>
              </div>
            </Td>
          </TRow>
        </tbody>
      </table>
    </div>
  </NeutralContainer>

  <ItemModals ref="modals" :detail="detail" :members="members" @items-changed="emit('itemsChanged')" @error="emit('error', $event)"/>
</template>
