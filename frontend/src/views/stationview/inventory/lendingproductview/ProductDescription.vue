/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import LinkButton from '@/components/button/LinkButton.vue'
import ProseContent from '@/components/display/ProseContent.vue'
import ProseExcerpt from '@/components/display/ProseExcerpt.vue'
import {renderMarkdown} from '@/util/markdown'

/**
 * A partner's Markdown description. Folded, it is cut at a few lines where it is taller than that,
 * measured on the page, and opens on request; unfolded, it is shown whole.
 */
const props = defineProps<{
  text: string
  /** Whether a long text starts cut, with a toggle to open it. */
  folded?: boolean
}>()

const {t} = useI18n()
const present = computed(() => props.text.trim() !== '')
const open = ref(false)
const cut = ref(false)
</script>

<template>
  <div v-if="present">
    <ProseContent v-if="!folded || open" v-html="renderMarkdown(text)"/>
    <ProseExcerpt v-else :html="renderMarkdown(text)" max-height="max-h-20" @measured="cut = $event"/>
    <LinkButton v-if="folded && (cut || open)" :aria-expanded="open" @click="open = !open">
      {{ open ? t('lendingProduct.less') : t('lendingProduct.more') }}
    </LinkButton>
  </div>
</template>
