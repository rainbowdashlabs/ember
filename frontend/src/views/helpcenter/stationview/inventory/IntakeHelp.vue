/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IntakeTable from '@/views/stationview/inventory/intakeview/IntakeTable.vue'
import {lineFor, type IntakeLine} from '@/views/stationview/inventory/intakeview/intakeLines'
import {StationPermission} from '@/api/types'
import {ref} from 'vue'

const {t} = useI18n()

const SIZES = [
  {id: 1, inventoryId: 1, label: '152', position: 0},
  {id: 2, inventoryId: 1, label: '164', position: 1},
]

/** A made-up member for the example table, named the way the real one is. */
function someone(id: number, name: string): IntakeLine {
  return lineFor({id, stationId: '1', accountId: id, name})
}

const lines = ref<IntakeLine[]>([
  {...someone(1, 'Tim Berger'), sizeId: '1', internalId: 'J-114'},
  {...someone(2, 'Lena Berger'), sizeId: '2'},
  someone(3, 'Mia Berger'),
])
const bulkSize = ref('1')
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryIntake.title')" :subtitle="t('helpCenter.inventoryIntake.subtitle')">
    <HelpPermissionGuard :permissions="[StationPermission.INVENTORY_CREATE_INTERNAL]"
                         :label="t('helpCenter.permissionLabel.inventoryCreate')">
      {{ t('helpCenter.inventoryIntake.permissionText') }}
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.inventoryIntake.whatIs')">
      <p>{{ t('helpCenter.inventoryIntake.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryIntake.notAMovementText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryIntake.whoTitle')">
      <p>{{ t('helpCenter.inventoryIntake.whoText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryIntake.tableTitle')">
      <p>{{ t('helpCenter.inventoryIntake.tableText') }}</p>
      <NeutralContainer class="mt-3">
        <IntakeTable v-model:lines="lines" v-model:bulk-size="bulkSize" :sizes="SIZES" :fields="[]" has-sizes/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryIntake.sortTitle')">
      <p>{{ t('helpCenter.inventoryIntake.sortText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryIntake.bulkTitle')">
      <p>{{ t('helpCenter.inventoryIntake.bulkText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryIntake.saveTitle')">
      <p>{{ t('helpCenter.inventoryIntake.saveText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryIntake.tip') }}</HelpTip>
  </HelpArticle>
</template>
