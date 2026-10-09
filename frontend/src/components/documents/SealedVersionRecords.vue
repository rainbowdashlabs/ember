/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import {RecordKind} from '@/api/documents'
import {downloadAuthed} from '@/util/downloadAuthed'

/**
 * The two downloads that come with a signed version besides the version itself: its signature record on
 * its own, to keep and print next to the document, and the version with the record joined as one copy to
 * hand out. Both are built and sealed by the station when they are downloaded.
 *
 * <p>Opened from the link in a signer's copy mail, the record's button takes the focus, so the reader
 * lands on what the mail promised.
 */
const props = defineProps<{
  version: number
  /** The document's file name, which the downloads carry with the version's number. */
  fileName: string
  url: (version: number, kind: RecordKind) => string
  /** Whether this version's record is the one the reader came for. */
  focused: boolean
}>()

const {t} = useI18n()
const recordButton = ref<InstanceType<typeof IconButton> | null>(null)

function nameOf(kind: RecordKind): string {
  const dot = props.fileName.lastIndexOf('.')
  const stem = dot <= 0 ? props.fileName : props.fileName.slice(0, dot)
  return `${stem}-v${props.version}-${kind}.pdf`
}

async function download(kind: RecordKind) {
  await downloadAuthed(props.url(props.version, kind), nameOf(kind))
}

onMounted(() => {
  if (props.focused) (recordButton.value?.$el as HTMLElement | undefined)?.focus()
})
</script>

<template>
  <IconButton ref="recordButton" :icon="['fas', 'file-lines']"
              :label="t('documents.sealedRecord', {version})"
              :class="focused ? 'ring-2 ring-primary' : ''"
              :data-testid="`document-version-record-${version}`"
              @click="download(RecordKind.RECORD)"/>
  <IconButton :icon="['fas', 'file-signature']"
              :label="t('documents.sealedWithRecord', {version})"
              :data-testid="`document-version-with-record-${version}`"
              @click="download(RecordKind.WITH_RECORD)"/>
</template>
