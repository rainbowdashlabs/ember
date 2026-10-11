/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {StationPermission} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {useReaderPeople} from '@/composables/useReaderPeople'
import MemberDocumentsPanel from './MemberDocumentsPanel.vue'
import ReaderPersonTabs from './ReaderPersonTabs.vue'
import type {DocumentOpening} from './documentOpening'
import {useReaderPersonChoice} from './readerPersonChoice'

/**
 * The reader's own documents and those of every member in their care, one person at a time, chosen by
 * a tab with their name where there is more than one.
 *
 * <p>Reading one's own documents needs nothing; adding to them is a right a station grants. The
 * documents of a child only read, adding is the child's own to do. Every person's list is loaded, the
 * others hidden, so a document a link asks for is found whoever it belongs to, and that person's tab
 * comes to the front with it open. The people are handed to the open document as well, which names
 * whom it belongs to from them, since a reader may not read the station's member list.
 */
withDefaults(defineProps<{
  /** A document a link asks to open, in whichever of the lists holds it. */
  opening?: DocumentOpening | null
}>(), {
  opening: null,
})

const {hasPermission} = useSession()
const people = useReaderPeople()
const {selected} = useReaderPersonChoice(people)

const canUploadOwn = computed(() => hasPermission(StationPermission.MEMBER_SELF_UPLOAD))
</script>

<template>
  <div v-if="people.length > 0" class="space-y-4">
    <ReaderPersonTabs v-model="selected" :people="people"/>
    <MemberDocumentsPanel
        v-for="person in people"
        v-show="person.id === selected"
        :key="person.id"
        :member-id="person.id"
        :can-upload="person.own && canUploadOwn"
        :all-members="people"
        :open-document-id="opening?.documentId ?? null"
        :focus-record="opening?.record ?? null"
        :on-asked-found="() => { selected = person.id }"
    />
  </div>
</template>
