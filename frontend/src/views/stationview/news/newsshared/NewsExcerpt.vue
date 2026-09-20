/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ProseExcerpt from '@/components/display/ProseExcerpt.vue'

/**
 * As much of an entry as the list of news is willing to show, with the words that say the rest is
 * on the entry's own page.
 */
defineProps<{contentHtml?: string}>()

const {t} = useI18n()
</script>

<template>
  <ProseExcerpt :html="contentHtml" :more-label="t('news.readMore')"/>
</template>
