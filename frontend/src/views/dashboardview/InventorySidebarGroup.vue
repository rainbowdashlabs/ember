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
import InventoryManageLinks from '@/views/dashboardview/inventorysidebargroup/InventoryManageLinks.vue'
import type {InventoryManageLink} from '@/views/dashboardview/inventorysidebargroup/inventoryManageLinks'
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
 * The entries about running the inventory, as this reader may reach them.
 *
 * <p>They stand under a heading of their own only where there are several. A member who may do
 * nothing but swap their own gear was shown a "Verwaltung" heading holding that one entry, which
 * names a job they do not have and hides the one thing they came for behind a fold.
 */
const manageLinks = computed<InventoryManageLink[]>(() => {
  const links: InventoryManageLink[] = []
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
  links.push({
    name: 'inventory-exchanges',
    to: '/station/inventory/exchanges',
    icon: ['fas', 'rotate'],
    label: t('sidebar.inventoryExchanges'),
    badge: counts.value.pendingExchanges,
  })
  if (hasPermission(StationPermission.INVENTORY_PROCUREMENT)) {
    links.push({
      name: 'inventory-procurement',
      to: '/station/inventory/procurement',
      icon: ['fas', 'folder-plus'],
      label: t('sidebar.inventoryProcurement'),
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
  if (hasPermission(StationPermission.INVENTORY_READ)) {
    links.push({
      name: 'inventory-collections',
      to: '/station/inventory/collections',
      icon: ['fas', 'box-open'],
      label: t('sidebar.inventoryCollections'),
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
                :badge="counts.pendingExchanges + counts.lendingRequests" :icon="['fas', 'boxes-stacked']" :label="t('sidebar.inventory')"
                prefix="/station/inventory" :to="hasPermission(StationPermission.INVENTORY_READ) ? '/station/inventory' : undefined"
                name="inventory-overview" @navigate="close">
    <SidebarLink v-if="counts.myInventoryCount > 0" :icon="['fas', 'boxes-stacked']" name="inventory-my" to="/station/inventory/my" @navigate="close">
      {{ t('sidebar.myInventory') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.INVENTORY_READ)" :icon="['fas', 'warehouse']" name="inventory-storage" to="/station/inventory/storage" @navigate="close">
      {{ t('sidebar.inventoryStorage') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.INVENTORY_READ)" :icon="['fas', 'hand-holding']" name="inventory-borrowed" to="/station/inventory/borrowed" @navigate="close">
      {{ t('sidebar.inventoryBorrowed') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.INVENTORY_ASSIGN)" :icon="['fas', 'user-plus']" name="inventory-assign" to="/station/inventory/assign" @navigate="close">
      {{ t('sidebar.inventoryAssign') }}
    </SidebarLink>
    <SidebarSubGroup v-if="hasPermission(StationPermission.INVENTORY_CHECK)" :icon="['fas', 'clipboard-check']" :label="t('sidebar.inventoryCheck')" prefix="/station/inventory/checks">
      <SidebarLink :icon="['fas', 'user-check']" name="inventory-check-member-overview" to="/station/inventory/checks/member" @navigate="close">
        {{ t('sidebar.inventoryCheckMember') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'box-open']" name="inventory-check-container-overview" to="/station/inventory/checks/container" @navigate="close">
        {{ t('sidebar.inventoryCheckContainer') }}
      </SidebarLink>
    </SidebarSubGroup>
    <SidebarLink v-if="hasPermission(StationPermission.INVENTORY_READ)" :icon="['fas', 'users']" name="inventory-members" to="/station/inventory/members" @navigate="close">
      {{ t('sidebar.inventoryMembers') }}
    </SidebarLink>
    <SidebarSubGroup v-if="manageLinks.length > 1" :icon="['fas', 'gears']" :label="t('sidebar.inventoryManageGroup')"
                     :badge="counts.pendingExchanges + counts.lendingRequests"
                     :prefix="manageLinks.map(link => link.to)">
      <InventoryManageLinks :links="manageLinks" @navigate="close"/>
    </SidebarSubGroup>
    <InventoryManageLinks v-else :links="manageLinks" @navigate="close"/>
  </SidebarGroup>
</template>
