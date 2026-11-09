/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import AppLink from '@/components/navigation/AppLink.vue'
import type {Procedure} from '@/api/generated/schema'

/**
 * What was already prepared for a date from its sign-ups: each procedure by name, linked, and the choice
 * between opening the first one and starting another set anyway.
 */
defineProps<{
  /** The procedures already prepared for the date. */
  existing: Procedure[]
  /** The occurrence, already written the way a reader reads a date. */
  dateLabel: string
}>()

const emit = defineEmits<{
  anyway: []
  open: [procedure: Procedure]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <Alert variant="info" data-testid="signup-procedure-existing">
      {{ t('signupLists.procedureExists', {count: existing.length, date: dateLabel}) }}
    </Alert>
    <ul class="space-y-1" data-testid="signup-procedure-existing-list">
      <li v-for="procedure in existing" :key="procedure.id">
        <AppLink :href="`/station/procedures/${procedure.id}`">{{ procedure.name }}</AppLink>
      </li>
    </ul>
    <ButtonRow align="end" class="pt-2">
      <SecondaryButton @click="emit('anyway')">{{ t('signupLists.procedureAnyway') }}</SecondaryButton>
      <PrimaryButton data-testid="signup-procedure-open" @click="emit('open', existing[0]!)">
        {{ t('signupLists.procedureOpen') }}
      </PrimaryButton>
    </ButtonRow>
  </div>
</template>
