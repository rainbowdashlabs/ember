/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TabBar from '@/components/navigation/TabBar.vue'
import type {ReaderPerson} from '@/composables/useReaderPeople'

/**
 * One tab per person whose documents the reader sees, the reader's own first; nothing where the reader
 * looks after nobody, since a single tab chooses nothing.
 */
const selected = defineModel<number | null>({required: true})

const props = defineProps<{
  people: ReaderPerson[]
}>()

const {t} = useI18n()

const tabs = computed(() => props.people.map(person => ({
  key: String(person.id),
  label: person.own ? t('documents.tabOwn') : person.name,
})))

const key = computed({
  get: () => String(selected.value ?? ''),
  set: value => { selected.value = Number(value) },
})
</script>

<template>
  <TabBar v-if="people.length > 1" v-model="key" :tabs="tabs" data-testid="reader-person-tabs"/>
</template>
