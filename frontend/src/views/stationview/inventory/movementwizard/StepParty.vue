/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {fromMember, type MemberOption} from '@/components/input/select/memberOption'
import {stationMembers} from '@/api'

/**
 * Who the movement is with: a member, or the station's own store.
 *
 * <p>Two recipients rather than two intentions, which is why this is its own question and not four more
 * purposes. The member menu here is the one every other screen asks with.
 */
const memberId = defineModel<number | null>('memberId', {required: true})
const forTheStore = defineModel<boolean>('forTheStore', {required: true})

const {t} = useI18n()

const members = ref<MemberOption[]>([])

const picked = computed({
  get: () => (memberId.value != null ? String(memberId.value) : ''),
  set: value => {
    memberId.value = value ? Number(value) : null
    if (value) forTheStore.value = false
  },
})

function chooseStore() {
  forTheStore.value = true
  memberId.value = null
}

onMounted(async () => {
  try {
    members.value = (await stationMembers.listMembers()).map(fromMember)
  } catch {
    members.value = []
  }
})
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('movements.wizard.party.title') }}</SubHeader>

    <div class="space-y-1">
      <FieldLabel>{{ t('movements.wizard.party.member') }}</FieldLabel>
      <MemberSelectInput v-model="picked" :members="members" data-testid="wizard-member"/>
    </div>

    <button
        :class="[
          'w-full rounded-theme border px-3 py-2 text-left text-sm transition-colors',
          forTheStore ? 'border-primary bg-primary/10' : 'border-(--border) hover:border-primary',
        ]"
        data-testid="wizard-party-store"
        type="button"
        @click="chooseStore"
    >
      {{ t('movements.wizard.party.store') }}
    </button>
  </div>
</template>
