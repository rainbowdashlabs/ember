/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import CheckMemberBody from '@/views/stationview/inventory/checkmemberview/CheckMemberBody.vue'
import {useMemberCheck} from '@/composables/useMemberCheck'
import {results, state} from './checkmemberhelp/fixtures'

const {t} = useI18n()

const check = useMemberCheck(ref(0), ref(state), ref(null))
for (const {itemId, result} of results) check.setResult(itemId, result)
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryCheckMember.title')" :subtitle="t('helpCenter.inventoryCheckMember.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryCheckMember.whatShown')">
      <p>{{ t('helpCenter.inventoryCheckMember.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCheckMember.statusTitle')">
      <p>{{ t('helpCenter.inventoryCheckMember.statusConfirmed') }}</p>
      <p>{{ t('helpCenter.inventoryCheckMember.statusNotInPossession') }}</p>
      <p>{{ t('helpCenter.inventoryCheckMember.statusLost') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCheckMember.dummyTitle')">
      <div class="space-y-6">
        <CheckMemberBody
            :hand-out-mode="check.handOutMode.value"
            :state="state"
            :check-mode="false"
            :unchecked-entries="check.uncheckedEntries.value"
            :all-marked="check.allMarked.value"
            :any-marked="check.anyMarked.value"
            :submitting="false"
            :item-results="check.itemResults.value"
            :item-notes="check.itemNotes.value"
            :slots-not-in-possession="check.slotsNotInPossession.value"
            :slot-procurements="check.slotProcurements.value"
            :slot-selections="check.slotSelections.value"
            :assigned-for-inventory="check.assignedForInventory"
            :available-for-inventory="check.availableForInventory"
            :empty-slot-count="check.emptySlotCount"
            :size-label="check.sizeLabel"
            :item-label="check.itemLabel"
            :movement-step="check.movementStep"
        />
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCheckMember.rapidCheckTitle')">
      <p>{{ t('helpCenter.inventoryCheckMember.rapidCheckText') }}</p>
      <p>{{ t('helpCenter.inventoryCheckMember.rapidCheckText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCheckMember.countTitle')">
      <p>{{ t('helpCenter.inventoryCheckMember.countText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCheckMember.exchangeTitle')">
      <p>{{ t('helpCenter.inventoryCheckMember.exchangeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCheckMember.partialTitle')">
      <p>{{ t('helpCenter.inventoryCheckMember.partialText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.scanShared.title')">
      <p>{{ t('helpCenter.scanShared.intro') }}</p>
      <p class="font-medium">{{ t('helpCenter.scanShared.continuousTitle') }}</p>
      <p>{{ t('helpCenter.scanShared.continuousText') }}</p>
      <ul class="list-disc pl-5 space-y-1">
        <li>{{ t('helpCenter.scanShared.tipPermission') }}</li>
        <li>{{ t('helpCenter.scanShared.tipDistance') }}</li>
        <li>{{ t('helpCenter.scanShared.tipNarrow') }}</li>
      </ul>

      <NeutralContainer class="flex justify-center gap-2">
        <SecondaryButton>{{ t('inventory.check.skip') }}</SecondaryButton>
        <ScanButton mode="continuous"/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCheckMember.correctTitle')">
      <p>{{ t('helpCenter.inventoryCheckMember.correctText') }}</p>
      <p>{{ t('helpCenter.inventoryCheckMember.correctNewText') }}</p>
      <p>{{ t('helpCenter.inventoryCheckMember.correctOldText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCheckMember.noSizeEditTitle')">
      <p>{{ t('helpCenter.inventoryCheckMember.noSizeEditText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCheckMember.actionsTitle')">
      <p>{{ t('helpCenter.inventoryCheckMember.actionsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryCheckMember.tip') }}</HelpTip>
  </HelpArticle>
</template>
