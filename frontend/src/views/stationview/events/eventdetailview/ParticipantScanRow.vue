/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import {PaperState, type PaperSubmission} from '@/api/generated/schema'
import RequirementStatusBadge from './RequirementStatusBadge.vue'
import ScanRejection from './ScanRejection.vue'
import {SCAN_TYPES, type ParticipantCopy} from './documentTiles'

/**
 * Where one participant stands with one document, for an event manager: a scan that waits is read,
 * confirmed or turned down here, and until one is confirmed the manager may hand one in for them.
 */
const props = defineProps<{
  copy: ParticipantCopy
  busy: boolean
}>()

const emit = defineEmits<{
  view: [paper: PaperSubmission]
  confirm: [paper: PaperSubmission]
  reject: [paper: PaperSubmission]
  handIn: [file: File]
}>()

const {t} = useI18n()

const paper = computed(() => props.copy.document.paper)
const waiting = computed(() => paper.value?.state === PaperState.SUBMITTED ? paper.value : null)
const confirmed = computed(() => paper.value?.state === PaperState.CONFIRMED)
</script>

<template>
  <li class="space-y-1" data-testid="documents-to-bring-participant">
    <div class="flex flex-wrap items-center gap-2">
      <span class="flex-1">{{ copy.name }}</span>
      <RequirementStatusBadge :document="copy.document"/>
      <IconButton v-if="paper" :icon="['fas', 'eye']" :label="t('events.documents.scanView')"
                  data-testid="document-scan-view" @click="emit('view', paper)"/>
    </div>
    <ButtonRow v-if="waiting" pair align="end">
      <ErrorButton :disabled="busy" data-testid="document-scan-reject" @click="emit('reject', waiting)">
        {{ t('events.documents.scanReject') }}
      </ErrorButton>
      <SuccessButton :disabled="busy" data-testid="document-scan-confirm" @click="emit('confirm', waiting)">
        {{ t('events.documents.scanConfirm') }}
      </SuccessButton>
    </ButtonRow>
    <div v-else-if="!confirmed" class="flex justify-end">
      <FileUploadButton :accept="SCAN_TYPES" :disabled="busy" data-testid="document-scan-hand-in"
                        @select="file => emit('handIn', file)">
        {{ t('events.documents.scanUploadConfirmed') }}
      </FileUploadButton>
    </div>
    <ScanRejection :paper="paper"/>
  </li>
</template>
