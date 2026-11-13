/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import RegistrationTableExport from '../eventdetailview/RegistrationTableExport.vue'
import {eventGroups} from '@/api'
import {presentFile} from '@/util/documentFile'
import type {ExportSeparator} from '@/util/exportFormat'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Takes the sign-ups of an event group away as a file: a spreadsheet in the separator asked for, or a
 * sheet to print with a column per part to tick. Asks the same way the registration table of an
 * appointment does.
 */
const props = defineProps<{
  eventGroupId: number
}>()

const {t} = useI18n()

const open = ref(false)
const failure = ref<Failure | null>(null)

async function download(format: 'csv' | 'pdf', separator: ExportSeparator) {
  failure.value = null
  try {
    await presentFile(await eventGroups.exportSignUps(props.eventGroupId, format, separator))
    open.value = false
  } catch (e) {
    failure.value = describeFailure(e, t)
    open.value = false
  }
}
</script>

<template>
  <div class="space-y-2">
    <SecondaryButton :icon="['fas', 'file-export']" data-testid="event-group-sign-ups-export" @click="open = true">
      {{ t('eventGroups.exportSignUps') }}
    </SecondaryButton>
    <FailureAlert v-if="failure" :failure="failure"/>
    <RegistrationTableExport v-model="open" @download="download"/>
  </div>
</template>
