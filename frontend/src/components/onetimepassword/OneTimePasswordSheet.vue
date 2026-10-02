/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import type {Sheet} from '@/util/printSheet'

/** The access details of a one-time password as they are read off the screen: each line, then the note. */
defineProps<{sheet: Sheet}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <MutedText tag="p" size="sm">{{ t('oneTimePassword.dialog.hint') }}</MutedText>
    <dl class="space-y-3">
      <div v-for="line in sheet.lines" :key="line.label">
        <dt><DetailLabel>{{ line.label }}</DetailLabel></dt>
        <dd class="font-mono text-lg break-all">{{ line.value }}</dd>
      </div>
    </dl>
    <MutedText tag="p" size="sm">{{ sheet.note }}</MutedText>
  </div>
</template>
