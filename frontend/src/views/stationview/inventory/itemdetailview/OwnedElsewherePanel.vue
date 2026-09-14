/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import MovementWizard from '../movementwizard/MovementWizard.vue'
import type {WizardPrefill} from '../movementwizard/useMovementWizard'
import type {InventoryItem} from '@/api/inventory'
import {MovementPurpose, type MovementPurposeName} from '@/api/movements'

/**
 * What a station may do with a piece of gear that belongs to the association above it.
 *
 * <p>Not describing it, which is the association's, and not passing it on to anybody else, which is
 * not the station's to give. What is left is handing it back, and asking for a different one. Both
 * start a movement that the association answers at its end.
 */
const props = defineProps<{
  item: InventoryItem
}>()

const emit = defineEmits<{
  started: []
}>()

const {t} = useI18n()

const asking = ref(false)
const prefill = ref<WizardPrefill>({})

function ask(purpose: MovementPurposeName) {
  prefill.value = {
    purpose,
    memberId: null,
    itemId: props.item.id,
    inventoryId: props.item.inventoryId,
    oldSizeId: props.item.sizeId ?? null,
    skip: ['purpose', 'party', 'subject'],
  }
  asking.value = true
}
</script>

<template>
  <NeutralContainer data-testid="owned-elsewhere" class="space-y-3">
    <SectionHeader>{{ t('itemDetail.ownedElsewhereTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('itemDetail.ownedElsewhereHint') }}</p>

    <ButtonRow pair>
      <SecondaryButton :icon="['fas', 'rotate-left']" @click="ask(MovementPurpose.RETURN)">
        {{ t('itemDetail.handBack') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'right-left']" @click="ask(MovementPurpose.EXCHANGE)">
        {{ t('itemDetail.askExchange') }}
      </SecondaryButton>
    </ButtonRow>

    <MovementWizard v-model="asking" :prefill="prefill" @started="emit('started')"/>
  </NeutralContainer>
</template>
