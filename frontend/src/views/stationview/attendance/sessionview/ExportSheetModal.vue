/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import type {SheetOptions} from '@/api/attendance'

/** As many blank lines as the backend will honour, which is as many as fit on a page. */
const MAX_BLANK_ROWS = 40

const open = defineModel<boolean>({required: true})

const props = defineProps<{
  /** The session's own title, which is what the sheet is headed with unless it is written over. */
  sessionTitle: string
  /** Whether the station prints the address of this installation, which the sheet starts from. */
  showsInstanceUrl: boolean
  exporting: boolean
}>()

const emit = defineEmits<{
  export: [options: SheetOptions]
}>()

const {t} = useI18n()

const signature = ref(false)
const title = ref('')
const blankRows = ref(0)
const instanceUrl = ref(true)

// Opening the dialog forgets what the last export asked for: a sheet is printed for one evening,
// and the options of the one before it are rarely the ones wanted again.
watch(open, isOpen => {
  if (!isOpen) return
  signature.value = false
  title.value = props.sessionTitle
  blankRows.value = 0
  instanceUrl.value = props.showsInstanceUrl
})

function submit() {
  emit('export', {
    signature: signature.value,
    title: title.value === props.sessionTitle ? undefined : title.value,
    blankRows: blankRows.value,
    instanceUrl: instanceUrl.value,
  })
}
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4" data-testid="export-sheet-modal">
      <SubHeader>{{ t('attendanceSession.exportOptions.title') }}</SubHeader>

      <ToggleSetting
          v-model="signature"
          :hint="t('attendanceSession.exportOptions.signatureHint')"
          :label="t('attendanceSession.exportOptions.signature')"
          data-testid="export-signature-toggle"
      />

      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceSession.exportOptions.documentTitle') }}</FieldLabel>
        <TextInput v-model="title" :placeholder="t('attendanceSession.exportOptions.documentTitlePlaceholder')"/>
        <p class="text-xs text-(--text-muted)">{{ t('attendanceSession.exportOptions.documentTitleHint') }}</p>
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceSession.exportOptions.blankRows') }}</FieldLabel>
        <NumberInput v-model="blankRows" :max="MAX_BLANK_ROWS" :min="0"/>
        <p class="text-xs text-(--text-muted)">{{ t('attendanceSession.exportOptions.blankRowsHint') }}</p>
      </div>

      <ToggleSetting
          v-model="instanceUrl"
          :hint="t('attendanceSession.exportOptions.instanceUrlHint')"
          :label="t('attendanceSession.exportOptions.instanceUrl')"
          data-testid="export-instance-url-toggle"
      />

      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="exporting" :icon="['fas', 'download']" data-testid="export-submit" @click="submit">
          {{ t('attendanceSession.export') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
