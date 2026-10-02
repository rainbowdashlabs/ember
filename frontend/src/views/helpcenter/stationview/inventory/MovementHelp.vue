/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpRoleToggle from '@/components/helpcenter/HelpRoleToggle.vue'
import type {HelpPerspective} from '@/components/helpcenter/HelpRoleToggle.vue'
import {StationPermission} from '@/api/types'
import {useDataTable} from '@/composables/useDataTable'
import type {MovementResponse} from '@/api/generated/schema'
import MovementQueueToolbar from '@/views/stationview/inventory/movementqueueview/MovementQueueToolbar.vue'
import MovementQueueList from '@/views/stationview/inventory/movementqueueview/MovementQueueList.vue'
import {useMovementColumns} from '@/views/stationview/inventory/movementqueueview/movementColumns'
import {queueOrder} from '@/views/stationview/inventory/movementqueueview/movementFilter'
import {movements} from './movementhelp/fixtures'

const {t} = useI18n()

const perspectives: HelpPerspective[] = [
  {key: 'member', label: t('helpCenter.roles.member'), permissions: [StationPermission.USER]},
  {key: 'guardian', label: t('helpCenter.roles.memberManager'), permissions: [StationPermission.MEMBER_GUARDIAN]},
  {key: 'manager', label: t('helpCenter.roles.manager'), permissions: [StationPermission.INVENTORY_MOVEMENTS]},
]
const activeView = ref('')
const managerView = computed(() => activeView.value === 'manager')

const table = useDataTable<MovementResponse>({
  id: 'help-inventory-movements',
  rows: movements,
  columns: useMovementColumns(() => movements),
  rowKey: movement => movement.id,
  fallbackSort: queueOrder,
})
</script>

<template>
  <HelpArticle :subtitle="t('helpCenter.inventoryMovements.subtitle')" :title="t('helpCenter.inventoryMovements.title')">
    <HelpSection :title="t('helpCenter.inventoryMovements.whatIs')">
      <p>{{ t('helpCenter.inventoryMovements.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryMovements.purposeTitle')">
      <p>{{ t('helpCenter.inventoryMovements.purposeIssue') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.purposeReturn') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.purposeExchange') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.purposeRequest') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryMovements.noStatusTitle')">
      <p>{{ t('helpCenter.inventoryMovements.noStatusText') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.noStatusRead') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.noStatusWhere') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryMovements.ownerTitle')">
      <p>{{ t('helpCenter.inventoryMovements.ownerText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryMovements.createTitle')">
      <p>{{ t('helpCenter.inventoryMovements.createText') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.createPrefilled') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.createNoFlow') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryMovements.filterTitle')">
      <p>{{ t('helpCenter.inventoryMovements.filterText') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.filterOrderText') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.datesText') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.filterExportText') }}</p>
      <HelpRoleToggle v-model="activeView" :perspectives="perspectives"/>
      <div class="space-y-3">
        <MovementQueueToolbar
            :picking="false"
            :all-picked="false"
            :picked-count="0"
            :busy="false"
            :can-export="managerView"
            :field-options="[]"
            :picked-fields="new Set()"
        />
        <MovementQueueList
            :table="table"
            :total="movements.length"
            :can-correct="managerView"
            :picking="false"
            :picked-ids="new Set()"
        />
      </div>
    </HelpSection>

    <HelpSection v-if="activeView === 'member' || activeView === ''"
                 :title="t('helpCenter.inventoryMovements.asMember')">
      <p>{{ t('helpCenter.inventoryMovements.asMemberText') }}</p>
    </HelpSection>

    <HelpSection v-if="activeView === 'guardian'" :title="t('helpCenter.inventoryMovements.asMemberManager')">
      <p>{{ t('helpCenter.inventoryMovements.asMemberManagerText') }}</p>
    </HelpSection>

    <HelpSection v-if="activeView === 'manager'" :title="t('helpCenter.inventoryMovements.asManager')">
      <p>{{ t('helpCenter.inventoryMovements.asManagerText') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.managerAcknowledge') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.managerName') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.managerForce') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.managerCorrect') }}</p>
      <p>{{ t('helpCenter.inventoryMovements.managerExport') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryMovements.tip') }}</HelpTip>
  </HelpArticle>
</template>
