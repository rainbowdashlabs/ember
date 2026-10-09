/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import FormRespondentLine from './FormRespondentLine.vue'
import type { FormListEntry, FormRespondent } from '@/api/generated/schema'

/**
 * A form the reader can answer, with one line for everybody they may answer it for: themselves and
 * each member in their care it is put to. A guardian sees for whom an answer is already on file.
 */
defineProps<{
  form: FormListEntry
}>()

const emit = defineEmits<{
  (e: 'fill', form: FormListEntry, respondent: FormRespondent): void
}>()

const { t } = useI18n()
</script>

<template>
  <NeutralContainer data-testid="available-form">
    <div class="space-y-3">
      <div class="space-y-1">
        <span class="font-medium">{{ form.title }}</span>
        <MutedIcon v-if="form.restricted" :icon="['fas', 'lock']" class="ml-1"/>
        <p v-if="form.description" class="text-xs text-(--text-muted)">{{ form.description }}</p>
        <p class="text-xs text-(--text-muted)">{{ form.responseCount }} {{ t('forms.responses') }}</p>
      </div>
      <div class="space-y-2">
        <FormRespondentLine
          v-for="respondent in form.respondents"
          :key="respondent.memberId"
          :respondent="respondent"
          :allow-edit="form.allowEdit"
          @fill="emit('fill', form, $event)"
        />
      </div>
    </div>
  </NeutralContainer>
</template>
