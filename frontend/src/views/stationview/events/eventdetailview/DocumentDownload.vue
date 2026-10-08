/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import {PaperState} from '@/api/generated/schema'
import RequirementStatusBadge from './RequirementStatusBadge.vue'
import ScanRejection from './ScanRejection.vue'
import {SCAN_TYPES, type ParticipantCopy} from './documentTiles'

/**
 * The download of one participant's copy, with where it stands, and the hand-in of its signed scan until
 * a scan is confirmed. Named after the participant where the reader acts for more than one, since the
 * copies differ by whose data they hold.
 */
const props = defineProps<{
  copy: ParticipantCopy
  named: boolean
  busy: boolean
}>()

const emit = defineEmits<{
  fetch: []
  handIn: [file: File]
}>()

const {t} = useI18n()

const confirmed = computed(() => props.copy.document.paper?.state === PaperState.CONFIRMED)
</script>

<template>
  <div class="space-y-1" data-testid="document-to-bring">
    <div class="flex flex-wrap items-center gap-2">
      <RequirementStatusBadge :document="copy.document"/>
      <ButtonRow align="end" class="ml-auto">
        <SecondaryButton :icon="['fas', 'download']" :disabled="busy" data-testid="document-to-bring-download"
                         @click="emit('fetch')">
          {{ named ? t('events.documents.downloadFor', {name: copy.name}) : t('common.download') }}
        </SecondaryButton>
        <FileUploadButton v-if="!confirmed" :accept="SCAN_TYPES" :disabled="busy" data-testid="document-to-bring-scan"
                          @select="file => emit('handIn', file)">
          {{ t('events.documents.scanUpload') }}
        </FileUploadButton>
      </ButtonRow>
    </div>
    <ScanRejection :paper="copy.document.paper"/>
  </div>
</template>
