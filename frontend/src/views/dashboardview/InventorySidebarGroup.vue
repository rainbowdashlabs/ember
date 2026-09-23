/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SidebarSubGroup from '@/components/navigation/SidebarSubGroup.vue'
import InventorySidebarLinks from '@/views/dashboardview/inventorysidebargroup/InventorySidebarLinks.vue'
import type {InventorySidebarLink} from '@/views/dashboardview/inventorysidebargroup/inventorySidebarLinks'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'

const props = defineProps<{
  openGroup: string | null
  isDesktop: boolean
}>()

const emit = defineEmits<{
  (e: 'update:openGroup', value: string | null): void
  (e: 'navigate'): void
}>()

const {t} = useI18n()
const {hasPermission} = useSession()
const {counts} = useSidebarCounts()

/**
 * The daily work: the gear itself, who holds it, and what is on the move.
 *
 * <p>Flat rather than folded, because these are the entries somebody opens several times a day.
 * What a station sets up once lives under a heading instead.
 */
const dailyLinks = computed<InventorySidebarLink[]>(() => {
  const links: InventorySidebarLink[] = []
  if (hasPermission(StationPermission.INVENTORY_READ)) {
    links.push({
      name: 'inventory-members',
      to: '/station/inventory/members',
      icon: ['fas', 'users'],
      label: t('sidebar.inventoryMembers'),
    })
  }
  if (hasPermission(StationPermission.INVENTORY_ASSIGN)) {
    links.push({
      name: 'inventory-assign',
      to: '/station/inventory/assign',
      icon: ['fas', 'user-plus'],
      label: t('sidebar.inventoryAssign'),
    })
  }
  if (hasPermission(StationPermission.INVENTORY_READ)) {
    links.push({
      name: 'inventory-storage',
      to: '/station/inventory/storage',
      icon: ['fas', 'warehouse'],
      label: t('sidebar.inventoryStorage'),
    })
  }
  links.push({
    name: 'inventory-movements',
    to: '/station/inventory/movements',
    icon: ['fas', 'rotate'],
    label: t('sidebar.inventoryMovements'),
    badge: counts.value.openMovements,
  })
  if (hasPermission(StationPermission.INVENTORY_PROCUREMENT)) {
    links.push({
      name: 'inventory-procurement',
      to: '/station/inventory/procurement',
      icon: ['fas', 'folder-plus'],
      label: t('sidebar.inventoryProcurement'),
    })
  }
  return links
})

/** What a station sets up once and then leaves alone: its inventories, its chains, what it asks of people. */
const settingsLinks = computed<InventorySidebarLink[]>(() => {
  const links: InventorySidebarLink[] = []
  if (hasPermission(StationPermission.INVENTORY_CREATE)) {
    links.push({
      name: 'inventory-manage',
      to: '/station/inventory/manage',
      icon: ['fas', 'box-open'],
      label: t('sidebar.inventoryManage'),
    })
  }
  if (hasPermission(StationPermission.INVENTORY_MANAGER)) {
    links.push({
      name: 'inventory-flows',
      to: '/station/inventory/flows',
      icon: ['fas', 'diagram-project'],
      label: t('sidebar.inventoryFlows'),
    })
  }
  if (hasPermission(StationPermission.INVENTORY_READ)) {
    links.push({
      name: 'inventory-requirements',
      to: '/station/inventory/requirements',
      icon: ['fas', 'clipboard-list'],
      label: t('sidebar.inventoryRequirements'),
    })
  }
  return links
})

/** Gear that is somebody else's: what this station borrowed, and what it lends out. */
const lendingLinks = computed<InventorySidebarLink[]>(() => {
  const links: InventorySidebarLink[] = []
  if (hasPermission(StationPermission.INVENTORY_READ)) {
    links.push({
      name: 'inventory-borrowed',
      to: '/station/inventory/borrowed',
      icon: ['fas', 'hand-holding'],
      label: t('sidebar.inventoryBorrowed'),
    })
  }
  if (hasPermission(StationPermission.INVENTORY_LENDING_REQUEST)
      || hasPermission(StationPermission.INVENTORY_LENDING_MANAGER)) {
    links.push({
      name: 'inventory-lending',
      to: '/station/inventory/lending',
      icon: ['fas', 'handshake'],
      label: t('sidebar.inventoryLending'),
      badge: counts.value.lendingRequests,
    })
  }
  return links
})

function close() {
  emit('navigate')
}
</script>

<template>
  <SidebarGroup :open-group="props.isDesktop ? undefined : props.openGroup" @update:open-group="v => emit('update:openGroup', v)"
                :badge="counts.openMovements + counts.lendingRequests" :icon="['fas', 'boxes-stacked']" :label="t('sidebar.inventory')"
                prefix="/station/inventory" :to="hasPermission(StationPermission.INVENTORY_READ) ? '/station/inventory' : undefined"
                name="inventory-overview" @navigate="close">
    <SidebarLink v-if="counts.myInventoryCount > 0" :icon="['fas', 'boxes-stacked']" name="inventory-my" to="/station/inventory/my" @navigate="close">
      {{ t('sidebar.myInventory') }}
    </SidebarLink>

    <InventorySidebarLinks :links="dailyLinks" @navigate="close"/>

    <SidebarSubGroup v-if="hasPermission(StationPermission.INVENTORY_CHECK)" :icon="['fas', 'clipboard-check']" :label="t('sidebar.inventoryCheck')" prefix="/station/inventory/checks">
      <SidebarLink :icon="['fas', 'user-check']" name="inventory-check-member-overview" to="/station/inventory/checks/member" @navigate="close">
        {{ t('sidebar.inventoryCheckMember') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'box-open']" name="inventory-check-container-overview" to="/station/inventory/checks/container" @navigate="close">
        {{ t('sidebar.inventoryCheckContainer') }}
      </SidebarLink>
    </SidebarSubGroup>

    <SidebarSubGroup v-if="settingsLinks.length > 0" :icon="['fas', 'gears']" :label="t('sidebar.inventorySettings')"
                     :prefix="settingsLinks.map(link => link.to)">
      <InventorySidebarLinks :links="settingsLinks" @navigate="close"/>
    </SidebarSubGroup>

    <SidebarSubGroup v-if="lendingLinks.length > 0" :icon="['fas', 'handshake']" :label="t('sidebar.inventoryLendingGroup')"
                     :badge="counts.lendingRequests"
                     :prefix="lendingLinks.map(link => link.to)">
      <InventorySidebarLinks :links="lendingLinks" @navigate="close"/>
    </SidebarSubGroup>
  </SidebarGroup>
</template>
