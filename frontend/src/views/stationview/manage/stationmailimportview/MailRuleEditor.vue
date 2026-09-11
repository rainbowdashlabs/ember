/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryContainer from '@/components/container/SecondaryContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import RuleSenderPatterns from './RuleSenderPatterns.vue'
import RuleFilterFields from './RuleFilterFields.vue'
import RuleToggles from './RuleToggles.vue'
import RuleTagList from './RuleTagList.vue'
import {MailRuleAction, MailTitleSource} from '@/api/mailImport'
import type {MailRule, MailRuleActionName, MailRuleRequest, MailTitleSourceName} from '@/api/mailImport'

/**
 * Writing a rule.
 *
 * <p>A rule cannot be saved without a sender, because an empty filter reads naturally as "everything" and
 * here that would be an open door left open by saying nothing.
 */
const props = defineProps<{
  /** The rule being changed, or null when one is being written. */
  rule: MailRule | null
  supportedTypes: string[]
  /** Where a new rule goes in the order. */
  position: number
}>()

const emit = defineEmits<{
  save: [request: MailRuleRequest]
  cancel: []
}>()

const {t} = useI18n()

const name = ref('')
const enabled = ref(true)
const subjectFilter = ref('')
const attachmentNameFilter = ref('')
const acceptedTypes = ref<string[]>([])
const minSizeKilobytes = ref(0)
const includeInline = ref(false)
const titleSource = ref<MailTitleSourceName>(MailTitleSource.SUBJECT)
const hidden = ref(false)
const keepOnArchive = ref(false)
const readSubjectForMember = ref(false)
const action = ref<MailRuleActionName>(MailRuleAction.MARK_SEEN)
const moveToFolder = ref('')
const senderPatterns = ref<string[]>([])
const tags = ref<string[]>([])

const canSave = computed(() =>
    name.value.trim() !== ''
    && senderPatterns.value.length > 0
    && acceptedTypes.value.length > 0
    && (action.value !== MailRuleAction.MOVE || moveToFolder.value.trim() !== ''))

watch(() => props.rule, (rule) => {
  name.value = rule?.name ?? ''
  enabled.value = rule?.enabled ?? true
  subjectFilter.value = rule?.subjectFilter ?? ''
  attachmentNameFilter.value = rule?.attachmentNameFilter ?? ''
  acceptedTypes.value = rule ? [...rule.acceptedTypes] : props.supportedTypes.slice(0, 1)
  minSizeKilobytes.value = rule ? Math.round(rule.minSizeBytes / 1024) : 0
  includeInline.value = rule?.includeInline ?? false
  titleSource.value = rule?.titleSource ?? MailTitleSource.SUBJECT
  hidden.value = rule?.hidden ?? false
  keepOnArchive.value = rule?.keepOnArchive ?? false
  readSubjectForMember.value = rule?.readSubjectForMember ?? false
  action.value = rule?.action ?? MailRuleAction.MARK_SEEN
  moveToFolder.value = rule?.moveToFolder ?? ''
  senderPatterns.value = rule ? [...rule.senderPatterns] : []
  tags.value = rule ? [...rule.tags] : []
}, {immediate: true})

function save() {
  emit('save', {
    name: name.value.trim(),
    position: props.rule?.position ?? props.position,
    enabled: enabled.value,
    subjectFilter: subjectFilter.value.trim() || null,
    attachmentNameFilter: attachmentNameFilter.value.trim() || null,
    acceptedTypes: acceptedTypes.value,
    minSizeBytes: Math.max(minSizeKilobytes.value, 0) * 1024,
    includeInline: includeInline.value,
    titleSource: titleSource.value,
    hidden: hidden.value,
    keepOnArchive: keepOnArchive.value,
    readSubjectForMember: readSubjectForMember.value,
    action: action.value,
    moveToFolder: action.value === MailRuleAction.MOVE ? moveToFolder.value.trim() : null,
    senderPatterns: senderPatterns.value,
    tags: tags.value,
  })
}
</script>

<template>
  <SecondaryContainer>
    <div class="space-y-4">
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div class="space-y-1">
          <FieldLabel>{{ t('mailImport.rule.name') }}</FieldLabel>
          <TextInput v-model="name" data-testid="rule-name"/>
        </div>
        <div class="flex items-end gap-2">
          <ToggleInput v-model="enabled" data-testid="rule-enabled"/>
          <span class="text-sm">{{ t('mailImport.rule.enabled') }}</span>
        </div>
      </div>

      <RuleSenderPatterns v-model="senderPatterns"/>

      <RuleFilterFields
          v-model:accepted-types="acceptedTypes"
          v-model:action="action"
          v-model:attachment-name-filter="attachmentNameFilter"
          v-model:min-size-kilobytes="minSizeKilobytes"
          v-model:move-to-folder="moveToFolder"
          v-model:subject-filter="subjectFilter"
          v-model:title-source="titleSource"
          :supported-types="supportedTypes"
      />

      <RuleTagList v-model="tags"/>

      <RuleToggles
          v-model:hidden="hidden"
          v-model:include-inline="includeInline"
          v-model:keep-on-archive="keepOnArchive"
          v-model:read-subject-for-member="readSubjectForMember"
      />

      <div class="flex flex-wrap justify-end gap-2">
        <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!canSave" data-testid="rule-save" @click="save">{{ t('common.save') }}</PrimaryButton>
      </div>
    </div>
  </SecondaryContainer>
</template>
