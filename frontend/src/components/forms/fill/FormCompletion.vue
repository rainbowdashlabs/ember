/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import AppIcon from '@/components/display/AppIcon.vue'

/**
 * What a form says once it is sent: its own message where it has one, and the general thanks where
 * it has none, with the link it offers to go on to.
 *
 * <p>Every place a form is filled in shows this, so a form set up with "see you on Saturday" says
 * it on the internal screen, on its own page and in a public page alike.
 */
const props = defineProps<{
  message?: string | null
  link?: string | null
  linkLabel?: string | null
}>()

const {t} = useI18n()

/** An address outside this site opens beside it, so the reader does not lose the page they sent from. */
const external = computed(() => !(props.link ?? '').startsWith('/'))
</script>

<template>
  <div class="space-y-2" data-testid="form-completion">
    <p class="text-sm whitespace-pre-line">{{ message || t('publicForm.thanksText') }}</p>
    <a v-if="link" :href="link" :target="external ? '_blank' : undefined" :rel="external ? 'noopener noreferrer' : undefined"
       class="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline">
      {{ linkLabel || link }}
      <AppIcon :icon="['fas', 'arrow-right']" class="h-3 w-3"/>
    </a>
  </div>
</template>
