/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SidebarSubGroup from '@/components/navigation/SidebarSubGroup.vue'
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
    <SidebarSubGroup :icon="['fas', 'gears']" :label="t('sidebar.inventoryManageGroup')" :badge="counts.pendingExchanges + counts.lendingRequests">
      <SidebarLink v-if="hasPermission(StationPermission.INVENTORY_CREATE)" :icon="['fas', 'box-open']" name="inventory-manage" to="/station/inventory/manage" @navigate="close">
        {{ t('sidebar.inventoryManage') }}
      </SidebarLink>
      <SidebarLink :badge="counts.pendingExchanges" :icon="['fas', 'rotate']" name="inventory-exchanges" to="/station/inventory/exchanges" @navigate="close">
        {{ t('sidebar.inventoryExchanges') }}
      </SidebarLink>
      <SidebarLink v-if="hasPermission(StationPermission.INVENTORY_PROCUREMENT)" :icon="['fas', 'folder-plus']" name="inventory-procurement" to="/station/inventory/procurement" @navigate="close">
        {{ t('sidebar.inventoryProcurement') }}
      </SidebarLink>
      <SidebarLink v-if="hasPermission(StationPermission.INVENTORY_READ)" :icon="['fas', 'clipboard-list']" name="inventory-requirements" to="/station/inventory/requirements" @navigate="close">
        {{ t('sidebar.inventoryRequirements') }}
      </SidebarLink>
      <SidebarLink v-if="hasPermission(StationPermission.INVENTORY_LENDING_REQUEST) || hasPermission(StationPermission.INVENTORY_LENDING_MANAGER)" :badge="counts.lendingRequests" :icon="['fas', 'handshake']" name="inventory-lending" to="/station/inventory/lending" @navigate="close">
        {{ t('sidebar.inventoryLending') }}
      </SidebarLink>
    </SidebarSubGroup>
  </SidebarGroup>
</template>
