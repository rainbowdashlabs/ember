/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FileThumbnail from '@/components/documents/FileThumbnail.vue'
import TemplatePickerModal from '@/components/documents/templatepicker/TemplatePickerModal.vue'
import {stationTemplateSource, usableTemplates} from '@/api/documentTemplates'
import {StationPermission, type StepDocument} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'

/**
 * Whether a step asks somebody to sign a document, and which one. Only templates outside appointments
 * are offered; whether the member or a guardian signs the chosen one is decided by the server, which
 * refuses a template nobody of theirs signs. Closing the picker without a choice leaves the step one
 * ticked by hand, so the switch never shows a document step that has none.
 *
 * <p>Only those who generate documents for members pick one, as the server demands; anybody else sees
 * the document a step asks for and may still make it a step ticked by hand.
 */
const document = defineModel<StepDocument | null>({required: true})

defineProps<{
  /** Whether the step already made its document, which fixes what it asks for. */
  locked?: boolean
}>()

const PICTURE_SIZE = 256

const {t} = useI18n()
const {hasPermission} = useSession()
const mayChoose = computed(() => hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER))
const picking = ref(false)
const asksToSign = ref(document.value != null)

function setAsking(asking: boolean) {
  asksToSign.value = asking
  if (!asking) document.value = null
  else if (document.value == null) picking.value = true
}

watch(picking, open => {
  if (!open && document.value == null) asksToSign.value = false
})

watch(document, chosen => {
  if (!picking.value) asksToSign.value = chosen != null
})
</script>

<template>
  <div v-if="mayChoose || asksToSign" class="space-y-2" data-testid="step-document">
    <div class="flex items-center gap-2">
      <FieldLabel>{{ t('procedures.stepDocument') }}</FieldLabel>
      <ToggleInput :model-value="asksToSign" :disabled="locked || (!mayChoose && !asksToSign)"
                   data-testid="step-document-toggle" @update:model-value="setAsking"/>
    </div>
    <p class="text-xs text-[var(--text-muted)]">
      {{ locked ? t('procedures.stepDocumentLocked') : mayChoose ? t('procedures.stepDocumentHint') : t('procedures.stepDocumentNotYours') }}
    </p>
    <div v-if="asksToSign" class="flex items-center gap-3">
      <template v-if="document?.name">
        <FileThumbnail v-if="mayChoose" :url="stationTemplateSource.pictureUrl(document.id, PICTURE_SIZE)" mime-type="application/pdf"
                       :alt="document.name" size="h-20 w-16" anchor-top/>
        <div class="min-w-0 flex-1 truncate font-medium" data-testid="step-document-name">{{ document.name }}</div>
      </template>
      <div v-else-if="document" class="min-w-0 flex-1 text-[var(--text-muted)]" data-testid="step-document-gone">
        {{ t('procedures.stepDocumentGone') }}
      </div>
      <SecondaryButton v-if="mayChoose && !locked" :icon="['fas', 'file-lines']" data-testid="step-document-open" @click="picking = true">
        {{ document ? t('documentTemplates.browse.chooseOther') : t('documentTemplates.chooseTemplate') }}
      </SecondaryButton>
    </div>
    <TemplatePickerModal v-if="mayChoose" v-model="picking" :pages="usableTemplates" :fixed="{forAppointments: false}"
                         :chosen-ids="document ? [document.id] : []"
                         @pick="picked => document = picked[0] ? {id: picked[0].id, name: picked[0].name} : document"/>
  </div>
</template>
