/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, shallowRef} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PdfCanvas from '@/components/documents/PdfCanvas.vue'
import PagePager from '@/components/documents/PagePager.vue'
import type {FormPreviewVersion} from '@/api/documentTemplates'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * A Word form drawn twice, side by side: in its own fonts and with the replacements chosen, so a line
 * that breaks elsewhere shows before the form is converted. Both are drawn when the pair appears and
 * again on request, since every drawing takes the server a few seconds. One page turner moves both. Where
 * the original has to show a look-alike for a font, it says so above it.
 */
const props = defineProps<{
  draw: (version: FormPreviewVersion) => Promise<Blob>
  /** Whether a drawing may be asked for now, false while a choice is not one the server takes. */
  ready: boolean
  /** The fonts the original shows a look-alike for, since neither the form nor a file carries them. */
  guessed: readonly string[]
}>()

const {t} = useI18n()
const VERSIONS: readonly FormPreviewVersion[] = ['original', 'replaced']

const drawings = shallowRef<Partial<Record<FormPreviewVersion, Blob>>>({})
const pageCounts = ref<Partial<Record<FormPreviewVersion, number>>>({})
const page = ref(1)
const pageCount = computed(() => Math.max(1, ...Object.values(pageCounts.value)))

const drawing = useAsyncAction(async () => {
  const [original, replaced] = await Promise.all(VERSIONS.map(version => props.draw(version)))
  drawings.value = {original, replaced}
  page.value = 1
})

function opened(version: FormPreviewVersion, count: number) {
  pageCounts.value = {...pageCounts.value, [version]: count}
}

onMounted(() => {
  if (props.ready) void drawing.run()
})
</script>

<template>
  <div class="space-y-3" data-testid="form-preview-pair">
    <SecondaryButton :icon="['fas', 'rotate']" :disabled="drawing.running.value || !ready"
                     data-testid="form-preview-refresh" @click="drawing.run()">
      {{ drawing.running.value ? t('documentTemplates.formImport.previewRunning') : t('documentTemplates.formImport.previewRefresh') }}
    </SecondaryButton>
    <FailureAlert :failure="drawing.failure.value"/>
    <div class="grid gap-4 sm:grid-cols-2">
      <div v-for="version in VERSIONS" :key="version" class="space-y-1">
        <MutedText tag="p" size="sm">{{ t(`documentTemplates.formImport.preview.${version}`) }}</MutedText>
        <Alert v-if="version === 'original' && guessed.length > 0" variant="info" data-testid="form-preview-guessed">
          {{ t('documentTemplates.formImport.previewGuessed', {fonts: guessed.join(', ')}) }}
        </Alert>
        <div class="flex aspect-[210/297] w-full overflow-hidden rounded-theme border border-bg-light-accent dark:border-bg-dark-accent"
             :data-testid="`form-preview-${version}`">
          <PdfCanvas :source="drawings[version] ?? null" :page="Math.min(page, pageCounts[version] ?? 1)" class="m-auto"
                     @loaded="count => opened(version, count)"/>
        </div>
      </div>
    </div>
    <PagePager v-if="pageCount > 1" v-model:page="page" :page-count="pageCount"
               :label="t('documentTemplates.formImport.previewPage', {page, count: pageCount})"/>
  </div>
</template>
