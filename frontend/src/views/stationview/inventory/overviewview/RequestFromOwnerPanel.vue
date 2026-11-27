/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MovementWizard from '../movementwizard/MovementWizard.vue'
import type {WizardPrefill} from '../movementwizard/useMovementWizard'
import ProcurementCreateModal from '../procurementview/ProcurementCreateModal.vue'
import {
  InventoryType,
  ItemOwner,
  MovementPurpose,
  type Inventory,
  type MemberWithName,
} from '@/api/generated/schema'

/**
 * Asking the association above for a piece the station does not have.
 *
 * <p>Where the association keeps its gear in Ember, the request goes to it as a movement: it names a kind
 * of thing and a size, and the association picks the actual piece when it sends one, or refuses with a
 * reason. That is why the wizard asks for an inventory rather than an item.
 *
 * <p>An association that keeps no gear in Ember can neither send in Ember nor refuse, so all of it is the
 * station's own work. There the station notes down what it asked for as a procurement of the
 * association's piece, for somebody or for the store, and records it as fulfilled once it arrives.
 */
const props = defineProps<{
  /** What the association above is called where it keeps its gear in Ember, or null where it does not. */
  ownerName: string | null
  /** The station's inventories, as the page already holds them. */
  inventories: Inventory[]
  /** The station's members, as the page already holds them. */
  members: MemberWithName[]
}>()

const emit = defineEmits<{
  requested: []
}>()

const {t} = useI18n()

const asking = ref(false)
const noting = ref(false)
const noted = ref(false)

/** What an association can be asked for: one thing in many copies that may be its own. */
const orderable = computed(() => props.inventories
    .filter(candidate => candidate.homogeneous && candidate.inventoryType !== InventoryType.INTERNAL))

const prefill: WizardPrefill = {
  purpose: MovementPurpose.REQUEST,
  memberId: null,
  skip: ['purpose', 'party'],
}

function noteDown() {
  noted.value = false
  noting.value = true
}

/**
 * Lets the page refresh once the noted request's dialog is closed. Refreshing while it is open would
 * take the dialog away with the confirmation it is showing.
 */
watch(noting, open => {
  if (!open && noted.value) emit('requested')
})
</script>

<template>
  <NeutralContainer data-testid="request-from-owner" class="space-y-3">
    <SectionHeader>{{ t('inventory.request.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">
      {{ props.ownerName
          ? t('inventory.request.hint', {name: props.ownerName})
          : t('inventory.request.hintOutside') }}
    </MutedText>

    <SecondaryButton v-if="props.ownerName" :icon="['fas', 'hand-holding']" @click="asking = true">
      {{ t('inventory.request.ask') }}
    </SecondaryButton>
    <SecondaryButton v-else :icon="['fas', 'hand-holding']" data-testid="request-note-down" @click="noteDown">
      {{ t('inventory.request.noteDown') }}
    </SecondaryButton>

    <MovementWizard v-if="props.ownerName" v-model="asking" :prefill="prefill" @started="emit('requested')"/>
    <ProcurementCreateModal
        v-else
        v-model="noting"
        :inventories="orderable"
        :members="members"
        :preset-owner="ItemOwner.CLUSTER"
        member-optional
        @created="noted = true"
    />
  </NeutralContainer>
</template>
