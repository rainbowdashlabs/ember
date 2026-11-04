/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import InviteDateRow from './InviteDateRow.vue'
import {emptyDate, MAX_INVITATION_DATES, type InvitationDateDraft} from './invitationDates'

/**
 * The dates an invitation offers, one row each, up to as many as one invitation carries. The last
 * row cannot be removed, because an invitation without a date is not one.
 */
const dates = defineModel<InvitationDateDraft[]>({required: true})

const props = defineProps<{duplicate: boolean}>()

const {t} = useI18n()

function addDate() {
  if (dates.value.length >= MAX_INVITATION_DATES) return
  dates.value = [...dates.value, emptyDate()]
}

function removeDate(index: number) {
  dates.value = dates.value.filter((_, at) => at !== index)
}

function updateDate(index: number, draft: InvitationDateDraft) {
  dates.value = dates.value.map((current, at) => at === index ? draft : current)
}
</script>

<template>
  <div class="space-y-3">
    <InviteDateRow
        v-for="(draft, index) in dates"
        :key="draft.id"
        :model-value="draft"
        :index="index"
        :removable="dates.length > 1"
        @update:model-value="updateDate(index, $event)"
        @remove="removeDate(index)"
    />
    <p class="text-xs text-(--text-muted)">{{ t('waitingList.inviteDatesHint') }}</p>
    <p class="text-xs text-(--text-muted)">{{ t('waitingList.inviteArrivalHint') }}</p>
    <Alert v-if="props.duplicate" variant="error">{{ t('waitingList.inviteDuplicateDate') }}</Alert>
    <SecondaryButton
        v-if="dates.length < MAX_INVITATION_DATES"
        :icon="['fas', 'plus']"
        data-testid="waitlist-invite-add-date"
        @click="addDate"
    >
      {{ t('waitingList.inviteAddDate') }}
    </SecondaryButton>
  </div>
</template>
