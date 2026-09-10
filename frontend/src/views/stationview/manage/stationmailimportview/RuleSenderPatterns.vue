/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import RemovableChip from './RemovableChip.vue'
import {isValidSenderPattern} from '@/api/mailImport'

/**
 * The senders a rule trusts.
 *
 * <p>A pattern is checked as it is typed rather than stored to match nothing, because this is the check
 * that decides whether a stranger can put files into the station's document store.
 */
const patterns = defineModel<string[]>({required: true})

const {t} = useI18n()

const typed = ref('')

const valid = computed(() => typed.value === '' || isValidSenderPattern(typed.value))
const canAdd = computed(() => typed.value.trim() !== '' && isValidSenderPattern(typed.value))

function add() {
  const candidate = typed.value.trim()
  if (!isValidSenderPattern(candidate)) return
  if (!patterns.value.includes(candidate)) patterns.value = [...patterns.value, candidate]
  typed.value = ''
}

function remove(pattern: string) {
  patterns.value = patterns.value.filter(candidate => candidate !== pattern)
}
</script>

<template>
  <div class="space-y-2">
    <FieldLabel>{{ t('mailImport.rule.senders') }}</FieldLabel>
    <MutedText size="sm" tag="p">{{ t('mailImport.rule.sendersHint') }}</MutedText>

    <div class="flex flex-wrap gap-2">
      <RemovableChip v-for="pattern in patterns" :key="pattern" :label="pattern" @remove="remove(pattern)"/>
    </div>

    <div class="flex flex-wrap items-start gap-2">
      <div class="min-w-0 flex-1 space-y-1">
        <TextInput v-model="typed" data-testid="rule-sender" placeholder="*@feuerwehr-musterstadt.de"
                   @keyup.enter="add"/>
        <ErrorBadge v-if="!valid">{{ t('mailImport.rule.senderInvalid') }}</ErrorBadge>
      </div>
      <SecondaryButton :disabled="!canAdd" data-testid="rule-sender-add" @click="add">
        {{ t('common.add') }}
      </SecondaryButton>
    </div>
  </div>
</template>
