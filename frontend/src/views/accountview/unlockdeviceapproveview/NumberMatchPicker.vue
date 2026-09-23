/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * The number the other device is showing, picked out of six.
 *
 * <p>This is what makes it safe for the code to travel inside the QR. Somebody who was sent a
 * picture of a QR is not looking at the screen that raised it, so they cannot answer this, and the
 * one chance in six of guessing it is a single chance: the wrong pick ends the request rather than
 * inviting another.
 *
 * <p>Picking approves outright. There is deliberately no confirm step behind it, because a reader
 * who has just matched a number against the device in front of them has already made the decision,
 * and a second button would only teach them to click past it.
 */
const {t} = useI18n()

defineProps<{
  choices: number[]
  busy?: boolean
}>()

const emit = defineEmits<{
  pick: [choice: number]
}>()
</script>

<template>
  <div class="space-y-3">
    <SectionHeader>{{ t('passkeys.approve.numberTitle') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('passkeys.approve.numberHint') }}</MutedText>
    <div class="grid grid-cols-3 gap-2">
      <button
          v-for="choice in choices"
          :key="choice"
          class="rounded-theme border border-(--border) py-4 text-2xl font-bold transition
                 hover:border-(--accent) hover:text-(--accent)
                 disabled:cursor-not-allowed disabled:opacity-50"
          :data-testid="`number-choice-${choice}`"
          :disabled="busy"
          type="button"
          @click="emit('pick', choice)"
      >
        {{ choice }}
      </button>
    </div>
    <MutedText tag="p" size="sm">{{ t('passkeys.approve.numberWrongWarning') }}</MutedText>
  </div>
</template>
