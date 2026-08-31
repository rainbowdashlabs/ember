/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SidebarExpandableLink from '@/components/navigation/SidebarExpandableLink.vue'

defineProps<{
  close: () => void
}>()

const {t} = useI18n()
</script>

<template>
  <SidebarGroup :icon="['fas', 'boxes-stacked']" :label="t('sidebar.inventory')"
                to="/helpcenter/station/inventory" name="help-inventory-module-overview" @navigate="close">
    <SidebarLink :icon="['fas', 'house']" name="help-inventory-overview"
                 to="/helpcenter/station/inventory/overview" @navigate="close">
      {{ t('sidebar.overview') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'boxes-stacked']" name="help-inventory-my"
                 to="/helpcenter/station/inventory/my" @navigate="close">
      {{ t('sidebar.myInventory') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'rotate']" name="help-inventory-exchanges"
                 to="/helpcenter/station/inventory/exchanges" @navigate="close">
      {{ t('sidebar.inventoryExchanges') }}
    </SidebarLink>
    <SidebarExpandableLink :icon="['fas', 'users']" name="help-inventory-members"
                           to="/helpcenter/station/inventory/members" prefix="/helpcenter/station/inventory/member" @navigate="close">
      <template #label>{{ t('sidebar.inventoryMembers') }}</template>
      <SidebarLink :icon="['fas', 'user']" name="help-inventory-member"
                   to="/helpcenter/station/inventory/member" @navigate="close">
        {{ t('sidebar.inventoryMemberDetail') }}
      </SidebarLink>
    </SidebarExpandableLink>
    <SidebarExpandableLink :icon="['fas', 'box-open']" name="help-inventory-manage"
                           to="/helpcenter/station/inventory/manage" @navigate="close">
      <template #label>{{ t('sidebar.inventoryManage') }}</template>
      <SidebarLink :icon="['fas', 'eye']" name="help-inventory-detail"
                   to="/helpcenter/station/inventory/detail" @navigate="close">
        {{ t('sidebar.inventoryDetail') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'pen']" name="help-inventory-edit"
                   to="/helpcenter/station/inventory/edit" @navigate="close">
        {{ t('sidebar.inventoryEdit') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'right-left']" name="help-inventory-move"
                   to="/helpcenter/station/inventory/move" @navigate="close">
        {{ t('helpCenter.inventoryMove.title') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'tag']" name="help-inventory-item-detail"
                   to="/helpcenter/station/inventory/item/0" @navigate="close">
        {{ t('helpCenter.itemDetail.title') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'table-columns']" name="help-inventory-intake"
                   to="/helpcenter/station/inventory/intake" @navigate="close">
        {{ t('helpCenter.inventoryIntake.title') }}
      </SidebarLink>
    </SidebarExpandableLink>
    <SidebarLink :icon="['fas', 'clipboard-list']" name="help-inventory-requirements"
                 to="/helpcenter/station/inventory/requirements" @navigate="close">
      {{ t('sidebar.inventoryRequirements') }}
    </SidebarLink>
    <SidebarExpandableLink :icon="['fas', 'clipboard-check']" name="help-inventory-checks"
                           to="/helpcenter/station/inventory/checks" @navigate="close">
      <template #label>{{ t('sidebar.inventoryCheck') }}</template>
      <SidebarLink :icon="['fas', 'users']" name="help-inventory-check-member-overview"
                   to="/helpcenter/station/inventory/checks/member" @navigate="close">
        {{ t('helpCenter.inventoryChecks.title') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'user-check']" name="help-inventory-check-member-detail"
                   to="/helpcenter/station/inventory/checks/0" @navigate="close">
        {{ t('sidebar.inventoryCheckMember') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'clipboard-list']" name="help-inventory-check-result"
                   to="/helpcenter/station/inventory/checks/0/result" @navigate="close">
        {{ t('sidebar.inventoryCheckResult') }}
      </SidebarLink>
    </SidebarExpandableLink>
    <SidebarLink :icon="['fas', 'folder-plus']" name="help-inventory-procurement"
                 to="/helpcenter/station/inventory/procurement" @navigate="close">
      {{ t('sidebar.inventoryProcurement') }}
    </SidebarLink>
    <SidebarExpandableLink :icon="['fas', 'handshake']" name="help-inventory-lending"
                           to="/helpcenter/station/inventory/lending" prefix="/helpcenter/station/inventory/lending" @navigate="close">
      <template #label>{{ t('sidebar.inventoryLending') }}</template>
      <SidebarLink :icon="['fas', 'calendar-xmark']" name="help-inventory-lending-blocks"
                   to="/helpcenter/station/inventory/lending/blocks" @navigate="close">
        {{ t('helpCenter.inventoryLendingBlocks.title') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'magnifying-glass']" name="help-inventory-lending-browse"
                   to="/helpcenter/station/inventory/lending/browse" @navigate="close">
        {{ t('helpCenter.inventoryLendingBrowse.title') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'file-lines']" name="help-inventory-lending-request"
                   to="/helpcenter/station/inventory/lending/request/0" @navigate="close">
        {{ t('helpCenter.inventoryLendingRequest.title') }}
      </SidebarLink>
      <SidebarLink :icon="['fas', 'plus']" name="help-inventory-lending-create"
                   to="/helpcenter/station/inventory/lending/request/new" @navigate="close">
        {{ t('helpCenter.inventoryLendingCreate.title') }}
      </SidebarLink>
    </SidebarExpandableLink>
  </SidebarGroup>
</template>
