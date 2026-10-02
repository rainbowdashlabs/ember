/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PatchNotesView from '~/views/PatchNotesView.vue'

definePageMeta({
  layout: 'default',
  name: 'patch-notes',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.patchNotes'),
})
</script>

<template>
  <PatchNotesView />
</template>
