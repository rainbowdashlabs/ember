/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/**
 * The end of one signature collected in person: it is filed with the person, and their copy goes to them
 * or their guardians. Leads on to the same person or to the next one.
 */
defineProps<{name: string}>()

defineEmits<{samePerson: []; nextPerson: []}>()

const {t} = useI18n()
const headingId = useId()

onMounted(() => document.getElementById(headingId)?.focus())
</script>

<template>
  <SuccessContainer class="space-y-3 max-w-3xl" data-testid="witness-done">
    <SectionHeader :id="headingId" tabindex="-1" class="focus:outline-none">{{ t('signing.witness.done.heading', {name}) }}</SectionHeader>
    <p>{{ t('signing.witness.done.hint', {name}) }}</p>
    <ButtonRow>
      <PrimaryButton type="button" data-testid="witness-next-person" @click="$emit('nextPerson')">
        {{ t('signing.witness.done.nextPerson') }}
      </PrimaryButton>
      <SecondaryButton type="button" data-testid="witness-same-person" @click="$emit('samePerson')">
        {{ t('signing.witness.done.samePerson', {name}) }}
      </SecondaryButton>
    </ButtonRow>
  </SuccessContainer>
</template>
