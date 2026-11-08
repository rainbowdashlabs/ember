/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MovementWizard from '../movementwizard/MovementWizard.vue'
import type {WizardPrefill} from '../movementwizard/useMovementWizard'
import {MovementPurpose} from '@/api/generated/schema'

/**
 * Asking the association above for a piece the station does not have.
 *
 * <p>The other movements a station raises are about a piece it is holding. This one is not: it names a
 * kind of thing and a size, and the association picks the actual piece when it sends one, or refuses
 * with a reason. That is why the wizard asks for an inventory rather than an item.
 *
 * <p>Every station has a Träger above it, whether or not it uses Ember, so the panel is always there. Where
 * the Träger is outside Ember, the station records what it sends, standing in for it.
 */
const props = defineProps<{
  /** What the Träger above is called where it uses Ember, or null where it does not. */
  ownerName: string | null
}>()

const emit = defineEmits<{
  requested: []
}>()

const {t} = useI18n()

const asking = ref(false)

const prefill: WizardPrefill = {
  purpose: MovementPurpose.REQUEST,
  memberId: null,
  skip: ['purpose', 'party'],
}
</script>

<template>
  <NeutralContainer data-testid="request-from-owner" class="space-y-3">
    <SectionHeader>{{ t('inventory.request.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">
      {{ t('inventory.request.hint', {name: props.ownerName ?? t('inventory.request.ownerFallback')}) }}
    </MutedText>

    <SecondaryButton :icon="['fas', 'hand-holding']" @click="asking = true">
      {{ t('inventory.request.ask') }}
    </SecondaryButton>

    <MovementWizard v-model="asking" :prefill="prefill" @started="emit('requested')"/>
  </NeutralContainer>
</template>
