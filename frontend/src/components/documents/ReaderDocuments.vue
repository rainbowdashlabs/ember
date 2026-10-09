/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import OwnDocuments from './OwnDocuments.vue'
import type {DocumentOpening} from './documentOpening'
import {managedMembers as managedMembersApi} from '@/api'
import {StationPermission, type ManagedMember} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'

/**
 * The reader's own documents, then those of every member in their care, each under their name.
 *
 * <p>Reading one's own documents needs nothing; adding to them is a right a station grants. The
 * documents of a child only read, adding is the child's own to do. With `offers` each list also shows
 * the documents that can be generated for that member.
 */
withDefaults(defineProps<{
  offers: boolean
  /** A document a link asks to open, in whichever of the lists holds it. */
  opening?: DocumentOpening | null
}>(), {
  opening: null,
})

const {t} = useI18n()
const {sessionInfo, hasPermission, isGuardian} = useSession()

const memberId = computed(() => sessionInfo.value?.member?.id ?? null)
const canUploadOwn = computed(() => hasPermission(StationPermission.MEMBER_SELF_UPLOAD))
const managed = ref<ManagedMember[]>([])

watch(memberId, async id => {
  managed.value = id && isGuardian() ? await managedMembersApi.listManaged() : []
}, {immediate: true})
</script>

<template>
  <div v-if="memberId" class="space-y-6">
    <OwnDocuments :member-id="memberId" :can-upload="canUploadOwn" :offers="offers" :opening="opening"/>
    <OwnDocuments
        v-for="child in managed"
        :key="child.id"
        :can-upload="false"
        :member-id="child.id"
        :offers="offers"
        :opening="opening"
        :title="t('profile.documentsOf', {name: child.name})"
    />
  </div>
</template>
