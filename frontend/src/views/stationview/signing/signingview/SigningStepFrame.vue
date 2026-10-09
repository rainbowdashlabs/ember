/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * One screen of the signing flow: where the reader stands ("Schritt 2 von 5"), its heading, what it asks,
 * and the buttons that lead on or back.
 *
 * <p>The heading takes the focus as the screen appears, so a keyboard or screen reader user starts reading
 * at the top of the new step instead of on a button that is gone.
 */
defineProps<{
  title: string
  position: number
  total: number
}>()

const {t} = useI18n()
const headingId = useId()

onMounted(() => document.getElementById(headingId)?.focus())
</script>

<template>
  <section :aria-labelledby="headingId" class="space-y-4 max-w-3xl" data-testid="signing-step">
    <MutedText tag="p" size="sm" data-testid="signing-progress">{{ t('signing.flow.progress', {position, total}) }}</MutedText>
    <SectionHeader :id="headingId" tabindex="-1" class="focus:outline-none">{{ title }}</SectionHeader>
    <slot/>
    <slot name="actions"/>
  </section>
</template>
