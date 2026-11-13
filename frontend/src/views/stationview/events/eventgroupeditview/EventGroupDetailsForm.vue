/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

/** What an event group holds besides its sets, audiences and parts: name, description and its sign-up. */
const name = defineModel<string>('name', {required: true})
const description = defineModel<string>('description', {required: true})
const limit = defineModel<number | undefined>('limit', {required: true})
const deadline = defineModel<string>('deadline', {required: true})
const confirm = defineModel<boolean>('confirm', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <FieldLabel for="event-group-name">{{ t('eventGroups.name') }}</FieldLabel>
    <TextInput id="event-group-name" v-model="name" data-testid="event-group-name"/>
    <FieldLabel>{{ t('eventGroups.description') }}</FieldLabel>
    <MarkdownEditor v-model="description"/>
    <FieldLabel for="event-group-limit">{{ t('eventGroups.limit') }}</FieldLabel>
    <NumberInput id="event-group-limit" v-model="limit"/>
    <FieldHint>{{ t('eventGroups.limitHint') }}</FieldHint>
    <FieldLabel for="event-group-deadline">{{ t('eventGroups.deadlineField') }}</FieldLabel>
    <DateTimeInput id="event-group-deadline" v-model="deadline"/>
    <FieldLabel inline>
      <ToggleInput v-model="confirm"/>
      {{ t('eventGroups.confirmationField') }}
    </FieldLabel>
  </NeutralContainer>
</template>
