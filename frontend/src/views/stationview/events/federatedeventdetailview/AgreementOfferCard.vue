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
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import type {MemberOption} from '@/components/input/select/memberOption'
import type {PartnerAgreementOffer} from '@/api/generated/schema'

interface EligibleMember {
  uid: string
  name: string
}

/**
 * The agreement a partner station's appointment without registrations offers on its page. Signing it, for
 * oneself or a member in one's care, says they will come: the document is filed in their documents and signed
 * here, and the sealed copy goes to the partner station.
 */
const selectedMemberUid = defineModel<string>('selectedMemberUid', {required: true})

const props = defineProps<{
  offers: PartnerAgreementOffer[]
  eligibleMembers: EligibleMember[]
  busy: boolean
}>()

const emit = defineEmits<{
  sign: [uid: string]
}>()

const {t} = useI18n()

const pickable = computed<MemberOption[]>(() =>
    props.eligibleMembers.map(member => ({value: member.uid, name: member.name})))

const chosenUid = computed(() =>
    props.eligibleMembers.length === 1 ? props.eligibleMembers[0]?.uid ?? '' : selectedMemberUid.value)
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="partner-agreement-offer">
    <SubHeader>{{ t('events.documents.partnerOffer.title') }}</SubHeader>
    <p v-for="offer in offers" :key="offer.templateId" class="text-sm font-medium">{{ offer.title }}</p>
    <MutedText size="sm" tag="p">{{ t('events.documents.partnerOffer.hint') }}</MutedText>
    <ButtonRow align="end">
      <MemberSelectInput
          v-if="eligibleMembers.length > 1"
          v-model="selectedMemberUid"
          class="w-56"
          :members="pickable"
          :placeholder="t('eventsUpcoming.selectMember')"
      />
      <PrimaryButton :icon="['fas', 'file-signature']" :disabled="busy || !chosenUid"
                     data-testid="partner-agreement-sign" @click="emit('sign', chosenUid)">
        {{ t('events.documents.signOnline') }}
      </PrimaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
