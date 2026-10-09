/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import type { FormRespondent } from '@/api/generated/schema'

/**
 * One person a form can be answered for, the reader or a member in their care, with whether an answer
 * is on file for them and the way to give or change it. A sent answer on a form whose answers cannot
 * be changed offers nothing more.
 */
const props = defineProps<{
  respondent: FormRespondent
  allowEdit: boolean
}>()

const emit = defineEmits<{
  (e: 'fill', respondent: FormRespondent): void
}>()

const { t } = useI18n()

const label = computed(() => props.respondent.self
    ? t('forms.respondent.self', { name: props.respondent.name })
    : props.respondent.name)
</script>

<template>
  <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2" data-testid="form-respondent">
    <div class="flex flex-wrap items-center gap-2">
      <span class="text-sm">{{ label }}</span>
      <SuccessBadge v-if="respondent.hasResponded">{{ t('forms.respondent.answered') }}</SuccessBadge>
      <SecondaryBadge v-else>{{ t('forms.respondent.pending') }}</SecondaryBadge>
    </div>
    <ButtonRow align="end">
      <PrimaryButton v-if="!respondent.hasResponded" @click="emit('fill', respondent)">
        {{ t('forms.fillForm') }}
      </PrimaryButton>
      <SecondaryButton v-else-if="allowEdit" @click="emit('fill', respondent)">
        {{ t('forms.editResponse') }}
      </SecondaryButton>
    </ButtonRow>
  </div>
</template>
