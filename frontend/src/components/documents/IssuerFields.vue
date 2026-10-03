/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import type {MemberOption} from '@/components/input/select/memberOption'

/**
 * Who issues a document: a current member of the station and what they do there, such as the title of
 * their office. The member's official name and the function fill the issuer's placeholders, and the
 * issuer's signature field belongs to them.
 */
const memberId = defineModel<number | null>('memberId', {required: true})
const issuerFunction = defineModel<string>('issuerFunction', {required: true})

defineProps<{
  /** The current members of the station, who may issue documents. */
  members: MemberOption[]
}>()

const {t} = useI18n()

function choose(value: string) {
  memberId.value = value ? Number(value) : null
}
</script>

<template>
  <div class="grid gap-4 sm:grid-cols-2">
    <LabelledField :label="t('documentTemplates.issuer.member')" hint>
      <MemberSelectInput :model-value="memberId === null ? '' : String(memberId)" :members="members" clearable
                         :empty-label="t('documentTemplates.issuer.nobody')" data-testid="issuer-member"
                         @update:model-value="choose"/>
    </LabelledField>
    <LabelledField :label="t('documentTemplates.issuer.function')" hint>
      <TextInput v-model="issuerFunction" :placeholder="t('documentTemplates.issuer.functionPlaceholder')"
                 data-testid="issuer-function"/>
    </LabelledField>
  </div>
</template>
