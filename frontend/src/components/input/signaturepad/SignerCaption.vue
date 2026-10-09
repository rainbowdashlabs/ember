/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * The name of whoever signs, under the place they sign: a line, then the name, as the document prints
 * it under the field.
 */
defineProps<{
  name: string
}>()

const {t} = useI18n()
</script>

<template>
  <div class="max-w-xl border-t border-(--text-muted) pt-1" data-testid="signer-caption">
    <MutedText tag="p" size="sm">
      <span class="sr-only">{{ t('signaturePad.signer') }}</span>{{ name }}
    </MutedText>
  </div>
</template>
