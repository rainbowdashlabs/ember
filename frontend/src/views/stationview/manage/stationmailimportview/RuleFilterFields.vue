/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MultiSelectInput from '@/components/input/select/MultiSelectInput.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import {MailRuleAction, MailTitleSource} from '@/api/mailImport'
import type {MailRuleActionName, MailTitleSourceName} from '@/api/mailImport'

/** What a rule takes and how it files it: the filters that judge each message and each attachment. */
const props = defineProps<{
  supportedTypes: string[]
}>()

const subjectFilter = defineModel<string>('subjectFilter', {required: true})
const attachmentNameFilter = defineModel<string>('attachmentNameFilter', {required: true})
const acceptedTypes = defineModel<string[]>('acceptedTypes', {required: true})
const minSizeKilobytes = defineModel<number>('minSizeKilobytes', {required: true})
const titleSource = defineModel<MailTitleSourceName>('titleSource', {required: true})
const action = defineModel<MailRuleActionName>('action', {required: true})
const moveToFolder = defineModel<string>('moveToFolder', {required: true})

const {t} = useI18n()

const typeOptions = computed(() => props.supportedTypes.map(type => ({value: type, label: type})))
</script>

<template>
  <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
    <LabelledField hint :label="t('mailImport.rule.subjectFilter')">
      <TextInput v-model="subjectFilter" data-testid="rule-subject-filter"/>
    </LabelledField>
    <LabelledField hint :label="t('mailImport.rule.attachmentNameFilter')">
      <TextInput v-model="attachmentNameFilter" data-testid="rule-name-filter"/>
    </LabelledField>
    <LabelledField :label="t('mailImport.rule.types')">
      <MultiSelectInput v-model="acceptedTypes" data-testid="rule-types" :options="typeOptions"/>
    </LabelledField>
    <LabelledField :help="t('mailImport.rule.minSizeHint')" :label="t('mailImport.rule.minSize')">
      <NumberInput v-model="minSizeKilobytes" data-testid="rule-min-size" :min="0"/>
    </LabelledField>
    <LabelledField :label="t('mailImport.rule.titleSource')">
      <SelectInput v-model="titleSource" data-testid="rule-title-source">
        <option :value="MailTitleSource.SUBJECT">{{ t('mailImport.titleSource.SUBJECT') }}</option>
        <option :value="MailTitleSource.FILE_NAME">{{ t('mailImport.titleSource.FILE_NAME') }}</option>
      </SelectInput>
    </LabelledField>
    <LabelledField :label="t('mailImport.rule.action')">
      <SelectInput v-model="action" data-testid="rule-action">
        <option :value="MailRuleAction.MARK_SEEN">{{ t('mailImport.action.MARK_SEEN') }}</option>
        <option :value="MailRuleAction.FLAG">{{ t('mailImport.action.FLAG') }}</option>
        <option :value="MailRuleAction.MOVE">{{ t('mailImport.action.MOVE') }}</option>
        <option :value="MailRuleAction.NOTHING">{{ t('mailImport.action.NOTHING') }}</option>
      </SelectInput>
    </LabelledField>
    <LabelledField v-if="action === MailRuleAction.MOVE" :label="t('mailImport.rule.moveToFolder')">
      <TextInput v-model="moveToFolder" data-testid="rule-move-folder"/>
    </LabelledField>
  </div>
</template>
