/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FormCompletion from './FormCompletion.vue'

/**
 * The screen a form leaves the reader on once it is sent, with the form's own words and link. What
 * the reader can do next, such as going back or changing the answer, goes in the slot.
 */
defineProps<{
  message?: string | null
  link?: string | null
  linkLabel?: string | null
}>()

const {t} = useI18n()
</script>

<template>
  <SuccessContainer data-testid="form-sent">
    <div class="space-y-3">
      <SectionHeader>{{ t('publicForm.thanksTitle') }}</SectionHeader>
      <FormCompletion :message="message" :link="link" :link-label="linkLabel"/>
      <slot/>
    </div>
  </SuccessContainer>
</template>
