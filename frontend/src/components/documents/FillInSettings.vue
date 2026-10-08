/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import SignerSelect from '@/components/documents/SignerSelect.vue'
import type {SignatureRole} from '@/api/generated/schema'

/** The longest label the server keeps. */
const MAX_LABEL = 100

/** The most characters any field takes, which is also what a field without a limit of its own takes. */
const MAX_LENGTH = 500

/**
 * What a field the signer fills in at signing asks for, in a letter or on a PDF: whose signature it goes
 * with, its label, whether it has to be filled in and how many characters it takes. A length left empty
 * takes as many as any field does.
 */
const signer = defineModel<SignatureRole | null | undefined>('signer', {required: true})
const label = defineModel<string>('label', {required: true})
const required = defineModel<boolean>('required', {required: true})
const maxLength = defineModel<number | null | undefined>('maxLength', {required: true})

const {t} = useI18n()

function setMaxLength(value: number | undefined) {
  maxLength.value = typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : null
}
</script>

<template>
  <div class="space-y-3" data-testid="fill-in-settings">
    <SignerSelect v-model="signer" :label="t('documentTemplates.fillIn.signerLabel')"
                  :help="t('documentTemplates.fillIn.signerHelp')"/>
    <LabelledField :label="t('documentTemplates.fillIn.label')" :help="t('documentTemplates.fillIn.labelHelp')">
      <TextInput :model-value="label" :maxlength="MAX_LABEL" data-testid="fill-in-label"
                 @update:model-value="label = $event ?? ''"/>
    </LabelledField>
    <ToggleSetting v-model="required" :label="t('documentTemplates.fillIn.required')"
                   :hint="t('documentTemplates.fillIn.requiredHint')" data-testid="fill-in-required"/>
    <LabelledField :label="t('documentTemplates.fillIn.maxLength')"
                   :help="t('documentTemplates.fillIn.maxLengthHelp', {max: MAX_LENGTH})" hint>
      <NumberInput :model-value="maxLength ?? undefined" :min="1" :max="MAX_LENGTH" data-testid="fill-in-max-length"
                   @update:model-value="setMaxLength"/>
    </LabelledField>
  </div>
</template>
