/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'

/** The red line where the part of a conversation the reader has not read yet starts. */
const {t} = useI18n()
</script>

<template>
  <div class="my-2 flex items-center gap-2" role="separator" data-testid="unread-line">
    <div class="h-px flex-1 bg-error"/>
    <span class="text-xs font-bold uppercase text-error">{{ t('messages.newLine') }}</span>
  </div>
</template>
