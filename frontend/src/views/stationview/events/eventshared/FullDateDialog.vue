/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import {WhenFull} from '@/api/generated/schema'
import type {FullDateQuestion} from '@/composables/useFullDateChoice'

/**
 * The question asked when whoever runs an appointment lets somebody in on a date whose places are
 * all taken.
 *
 * <p>Signing somebody up offers the waiting list and going beyond the limit; accepting a sign-up only
 * asks whether to go beyond it. Either says how the places stand, where the screen knows.
 */
const props = defineProps<{
  question: FullDateQuestion | null
}>()

const emit = defineEmits<{
  answer: [choice: WhenFull | null]
}>()

const {t} = useI18n()

const open = computed({
  get: () => props.question !== null,
  set: shown => {
    if (!shown) emit('answer', null)
  },
})

const placesLine = computed(() => {
  const places = props.question?.places
  return places ? t('registrationQueue.placesTaken', {taken: places.taken, limit: places.limit}) : ''
})
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4" data-testid="full-date-dialog">
      <SubHeader>
        {{ question?.kind === 'accept' ? t('registrationQueue.acceptAnywayTitle') : t('registrationQueue.fullTitle') }}
      </SubHeader>
      <p v-if="placesLine">{{ placesLine }}</p>
      <p>{{ question?.kind === 'accept' ? t('registrationQueue.acceptAnywayBody') : t('registrationQueue.fullBody') }}</p>
      <ButtonRow align="end">
        <SecondaryButton data-cancel @click="emit('answer', null)">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton
            v-if="question?.kind === 'signUp'"
            :icon="['fas', 'list-ol']"
            data-testid="full-date-queue"
            @click="emit('answer', WhenFull.QUEUE)"
        >
          {{ t('registrationQueue.putOnList') }}
        </PrimaryButton>
        <ErrorButton :icon="['fas', 'check']" data-testid="full-date-over-limit" @click="emit('answer', WhenFull.OVER_LIMIT)">
          {{ question?.kind === 'accept' ? t('registrationQueue.acceptAnyway') : t('registrationQueue.overLimit') }}
        </ErrorButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
