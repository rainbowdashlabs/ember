/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import RequirementStatusBadge from './RequirementStatusBadge.vue'
import type {ParticipantCopy} from './documentTiles'

/**
 * The download of one participant's copy, with where it stands. Named after the participant where the
 * reader acts for more than one, since the copies differ by whose data they hold.
 */
defineProps<{
  copy: ParticipantCopy
  named: boolean
  busy: boolean
}>()

const emit = defineEmits<{
  fetch: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center gap-2" data-testid="document-to-bring">
    <RequirementStatusBadge :document="copy.document"/>
    <SecondaryButton :icon="['fas', 'download']" :disabled="busy" class="ml-auto" data-testid="document-to-bring-download"
                     @click="emit('fetch')">
      {{ named ? t('events.documents.downloadFor', {name: copy.name}) : t('common.download') }}
    </SecondaryButton>
  </div>
</template>
