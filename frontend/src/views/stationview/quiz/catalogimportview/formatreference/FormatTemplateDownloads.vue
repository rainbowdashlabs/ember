/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {quiz} from '@/api'
import {describeFailure, type Failure} from '@/util/failure'

const props = defineProps<{
  format: 'csv' | 'json'
}>()

const {t} = useI18n()

const failure = ref<Failure | null>(null)

async function download() {
  failure.value = null
  try {
    await quiz.downloadCatalogTemplate(props.format)
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}
</script>

<template>
  <div class="space-y-1">
    <SecondaryButton :icon="['fas', 'download']" @click="download">
      {{ format === 'csv' ? t('quiz.format.downloadSheet') : t('quiz.format.downloadFile') }}
    </SecondaryButton>
    <MutedText v-if="!failure" class="block text-xs">{{ t('quiz.format.downloadHint') }}</MutedText>
    <FailureAlert :failure="failure"/>
  </div>
</template>
