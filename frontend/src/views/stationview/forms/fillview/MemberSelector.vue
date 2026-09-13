/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import InfoContainer from '@/components/container/InfoContainer.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type { MemberOption } from '@/components/input/select/memberOption'

type Option = { id: number | null; label: string }

const props = defineProps<{
  options: Option[]
}>()

const selected = defineModel<number | null>({ required: true })

const { t } = useI18n()

/**
 * Whom the form is being filled for.
 *
 * <p>The reader themselves is one of the answers and has no member id of their own here, so it is the
 * menu's empty row rather than a person in the list.
 */
const people = computed<MemberOption[]>(() => props.options
    .filter(option => option.id != null)
    .map(option => ({value: String(option.id), name: option.label})))

const ownLabel = computed(() => props.options.find(option => option.id == null)?.label)

const chosen = computed({
  get: () => (selected.value != null ? String(selected.value) : ''),
  set: value => {
    selected.value = value ? Number(value) : null
  },
})
</script>

<template>
  <InfoContainer>
    <div class="space-y-2">
      <p class="text-sm font-medium">{{ t('forms.fillForWhom') }}</p>
      <MemberSelectInput
          v-model="chosen"
          :members="people"
          :clearable="!!ownLabel"
          :empty-label="ownLabel"
          :placeholder="ownLabel ?? t('forms.fillForWhom')"
      />
      <MutedText v-if="selected" tag="p">{{ t('forms.fillForMemberHint') }}</MutedText>
    </div>
  </InfoContainer>
</template>
