/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {SignatureRole} from '@/api/generated/schema'
import {SIGNERS} from './signers'

/**
 * Who signs in a signature field of a PDF or on a signature line of a letter. Every signer gets an empty
 * field in the document, named after them, which the signing feature fills later.
 */
const role = defineModel<SignatureRole | null | undefined>({required: true})

const props = defineProps<{
  /** The label in place of the one for a signature, as a field to fill in has. */
  label?: string
  /** The line under the choice in place of the one for a signature. */
  help?: string
  /** The signers on offer, all of them unless a narrower list is given. */
  signers?: readonly SignatureRole[]
}>()

const {t} = useI18n()
</script>

<template>
  <LabelledField :label="label ?? t('documentTemplates.signerLabel')" :help="help ?? t('documentTemplates.signerHelp')">
    <SelectInput :model-value="role" data-testid="signer-select"
                 @update:model-value="value => role = value as SignatureRole">
      <option v-for="signer in props.signers ?? SIGNERS" :key="signer" :value="signer">{{ t(`documentTemplates.signer.${signer}`) }}</option>
    </SelectInput>
  </LabelledField>
</template>
