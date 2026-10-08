/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import {SignatureRole} from '@/api/generated/schema'

/**
 * What the signer of a signature line or a PDF signature field confirms. The statement is shown on the
 * signing screen and bound into the signature, so it is the declaration the signer makes. Left empty,
 * the field asks for the default statement of its signer in the template's language, which the input
 * shows as its placeholder: a guardian's names the member and declares custody of them.
 *
 * <p>Whether one guardian or each guardian signs is the signer picked beside it.
 */
const statement = defineModel<string | null | undefined>({required: true})

const props = defineProps<{
  /** Who signs, which decides the default statement. */
  role: SignatureRole | null | undefined
}>()

const {t} = useI18n()

/** The longest statement the server keeps. */
const MAX_STATEMENT = 500

const GUARDIANS: readonly SignatureRole[] = [
  SignatureRole.GUARDIAN_1,
  SignatureRole.GUARDIAN_2,
  SignatureRole.EACH_GUARDIAN,
  SignatureRole.ANY_GUARDIAN,
]

const defaultStatement = computed(() => {
  if (props.role === SignatureRole.ISSUER) return t('documentTemplates.statement.default.issuer')
  if (props.role && GUARDIANS.includes(props.role)) return t('documentTemplates.statement.default.guardian')
  return t('documentTemplates.statement.default.own')
})

const tooLong = computed(() => (statement.value ?? '').length > MAX_STATEMENT)
</script>

<template>
  <LabelledField :label="t('documentTemplates.statement.label')" hint
                 :help="tooLong ? t('documentTemplates.statement.tooLong', {max: MAX_STATEMENT}) : t('documentTemplates.statement.help')">
    <TextAreaInput :model-value="statement ?? ''" :rows="3" :placeholder="defaultStatement"
                   data-testid="signature-statement" @update:model-value="value => statement = value"/>
  </LabelledField>
</template>
