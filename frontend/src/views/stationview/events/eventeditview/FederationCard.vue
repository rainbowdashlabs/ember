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
import type {PartnerResponse} from '@/api/federation'

const props = defineProps<{
  partners: PartnerResponse[]
  canFederate: boolean
}>()

const shared = defineModel<boolean>('shared', {required: true})
const scope = defineModel<string>('scope', {required: true})
const partnerIds = defineModel<number[]>('partnerIds', {required: true})

/**
 * What each partner may do with this appointment, keyed by partner.
 *
 * <p>Only the partners this is actually shared with are worth arranging anything for, so the list
 * follows the share above rather than naming everybody the station has ever federated with.
 */
const places = defineModel<Record<number, {decides: boolean; budget: number | null}>>('places', {required: true})

const sharedWith = computed(() => {
  if (!shared.value) return []
  if (scope.value === 'SPECIFIC_PARTNERS') {
    return props.partners.filter(entry => partnerIds.value.includes(entry.partner.id))
  }
  return props.partners
})

function placesFor(partnerId: number) {
  return places.value[partnerId] ?? {decides: false, budget: null}
}

function setPlaces(partnerId: number, next: {decides: boolean; budget: number | null}) {
  places.value = {...places.value, [partnerId]: next}
}

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2">
    <SubHeader>{{ t('events.federation') }}</SubHeader>
    <p class="text-xs text-(--text-muted)">{{ t('events.federationShareHint') }}</p>
    <FederationSharePicker
        v-model:shared="shared"
        v-model:scope="scope"
        v-model:partner-ids="partnerIds"
        :partners="partners"
        :disabled="!canFederate"
        :no-permission-hint="t('events.federationNoPermission')"
    />

    <template v-if="sharedWith.length > 0">
      <SubHeader class="pt-2">{{ t('events.partnerPlacesTitle') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('events.partnerPlacesHint') }}</MutedText>
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
