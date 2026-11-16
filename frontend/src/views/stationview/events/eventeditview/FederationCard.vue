/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FederationSharePicker from '@/components/input/FederationSharePicker.vue'
import PartnerPlacesRow from '@/views/stationview/events/eventeditview/federationcard/PartnerPlacesRow.vue'
import MutedText from '@/components/typography/MutedText.vue'
import HintIcon from '@/components/typography/HintIcon.vue'
import HintedSubHeader from '@/components/typography/HintedSubHeader.vue'
import {ShareScope, type PartnerResponse} from '@/api/generated/schema'

const props = defineProps<{
  partners: PartnerResponse[]
  canFederate: boolean
  /** What sharing does, where the owner is not an event: an event template says so in its own words. */
  hint?: string
  /** A note on the share as it was taken over from a template, shown above the picker. */
  note?: string
  /** Without a heading: the switch says what it does, and the explanation sits behind a hint icon. */
  compact?: boolean
}>()

const shared = defineModel<boolean>('shared', {required: true})
const scope = defineModel<string>('scope', {required: true})
const partnerIds = defineModel<number[]>('partnerIds', {required: true})

/**
 * What each partner may do with this appointment, keyed by partner.
 *
 * <p>Only the partners this is actually shared with are worth arranging anything for, so the list
 * follows the share above rather than naming everybody the station has ever federated with. An
 * owner that hands out no places, such as an event template, binds none and the list is not shown.
 */
const places = defineModel<Record<number, {decides: boolean; budget: number | null}>>('places')

const sharedWith = computed(() => {
  if (!shared.value || !places.value) return []
  if (scope.value === ShareScope.SPECIFIC) {
    return props.partners.filter(entry => partnerIds.value.includes(entry.partner.id))
  }
  return props.partners
})

function placesFor(partnerId: number) {
  return places.value?.[partnerId] ?? {decides: false, budget: null}
}

function setPlaces(partnerId: number, next: {decides: boolean; budget: number | null}) {
  places.value = {...places.value, [partnerId]: next}
}

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2">
    <template v-if="!compact">
      <SubHeader>{{ t('events.federation') }}</SubHeader>
      <p class="text-xs text-(--text-muted)">{{ hint ?? t('events.federationShareHint') }}</p>
    </template>
    <p v-if="note" class="text-xs text-(--text-muted)" data-testid="federation-template-note">{{ note }}</p>
    <div class="flex items-start gap-1">
      <FederationSharePicker
          v-model:shared="shared"
          v-model:scope="scope"
          v-model:partner-ids="partnerIds"
          class="grow"
          :partners="partners"
          :disabled="!canFederate"
          :no-permission-hint="t('events.federationNoPermission')"
      />
      <HintIcon v-if="compact" class="mt-1" :text="hint ?? t('events.federationShareHint')"/>
    </div>

    <template v-if="sharedWith.length > 0">
      <HintedSubHeader v-if="compact" class="pt-2" :hint="t('events.partnerPlacesHint')">
        {{ t('events.partnerPlacesTitle') }}
      </HintedSubHeader>
      <template v-else>
        <SubHeader class="pt-2">{{ t('events.partnerPlacesTitle') }}</SubHeader>
        <MutedText tag="p" size="sm">{{ t('events.partnerPlacesHint') }}</MutedText>
      </template>
      <div class="space-y-3">
        <PartnerPlacesRow
            v-for="entry in sharedWith"
            :key="entry.partner.id"
            :partner-name="entry.partnerStationName"
            :disabled="!canFederate"
            :decides="placesFor(entry.partner.id).decides"
            :budget="placesFor(entry.partner.id).budget"
            @update:decides="decides => setPlaces(entry.partner.id, {...placesFor(entry.partner.id), decides})"
            @update:budget="budget => setPlaces(entry.partner.id, {...placesFor(entry.partner.id), budget})"
        />
      </div>
    </template>
  </NeutralContainer>
</template>
