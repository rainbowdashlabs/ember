/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FieldSetRow from './fieldsetspanel/FieldSetRow.vue'
import type {EditableField} from '@/api/profileFields'
import {membersOfSet} from '@/util/profileFields'
import type {FieldSet, FieldSetRequest} from '@/api/generated/schema'

/**
 * The groups the owner keeps its fields in.
 *
 * <p>A group bundles fields that belong together: they are asked of an audience together and stand
 * together on every form, under the group's name where it is a heading. Which group a field is in is
 * chosen where the field is edited.
 */
const props = defineProps<{
  sets: FieldSet[]
  fields: EditableField[]
}>()

const emit = defineEmits<{
  create: [name: string]
  update: [set: FieldSet, patch: Partial<FieldSetRequest>]
  dissolve: [set: FieldSet]
}>()

const {t} = useI18n()

const newName = ref('')

function fieldNamesOf(set: FieldSet): string[] {
  return membersOfSet(props.fields, set.id).map(field => field.name)
}

function create() {
  const name = newName.value.trim()
  if (!name) return
  emit('create', name)
  newName.value = ''
}
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="field-sets">
    <SectionHeader>{{ t('membersConfig.sets.title') }}</SectionHeader>
    <MutedText size="sm" tag="p">{{ t('membersConfig.sets.hint') }}</MutedText>

    <FieldSetRow
        v-for="set in props.sets"
        :key="set.id"
        :set="set"
        :field-names="fieldNamesOf(set)"
        @update="(s: FieldSet, patch: Partial<FieldSetRequest>) => emit('update', s, patch)"
        @dissolve="(s: FieldSet) => emit('dissolve', s)"/>

    <form class="flex flex-wrap items-center gap-2" @submit.prevent="create">
      <TextInput v-model="newName" class="min-w-0 flex-1" :placeholder="t('membersConfig.sets.newName')"
                 data-testid="field-set-new-name"/>
      <PrimaryButton :icon="['fas', 'plus']" :disabled="!newName.trim()" data-testid="field-set-create" @click="create">
        {{ t('membersConfig.sets.create') }}
      </PrimaryButton>
    </form>
  </NeutralContainer>
</template>
