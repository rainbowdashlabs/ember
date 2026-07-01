/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import type {StationMember} from '@/api/types'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const props = defineProps<{
  members: StationMember[]
}>()

const emit = defineEmits<{
  grant: [memberId: number, closesAt: string | null]
}>()

const {t} = useI18n()

const accessMemberSearch = ref('')
const accessMemberId = ref<number | undefined>(undefined)
const accessClosesAt = ref('')

const filteredMembers = computed(() => {
  const search = accessMemberSearch.value.toLowerCase().trim()
  if (!search) return props.members.slice(0, 10)
  return props.members.filter(m => m.name?.toLowerCase().includes(search) || m.email?.toLowerCase().includes(search)).slice(0, 10)
})

function selectAccessMember(m: StationMember) {
  accessMemberId.value = m.id
  accessMemberSearch.value = m.name ?? m.email ?? ''
}

function grantAccess() {
  if (!accessMemberId.value) return
  emit('grant', accessMemberId.value, accessClosesAt.value ? new Date(accessClosesAt.value).toISOString() : null)
  accessMemberId.value = undefined
  accessMemberSearch.value = ''
  accessClosesAt.value = ''
}
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('quiz.tests.accessManagement') }}</SubHeader>
    <NeutralContainer>
      <div class="flex flex-col sm:flex-row gap-3 items-start sm:items-end">
        <div class="flex-1 relative">
          <FieldLabel hint class="mb-1">{{ t('quiz.tests.grantAccessMember') }}</FieldLabel>
          <SearchInput v-model="accessMemberSearch" :placeholder="t('quiz.tests.searchMember')" />
          <div v-if="accessMemberSearch && !accessMemberId && filteredMembers.length > 0"
               class="absolute z-10 top-full mt-1 w-full rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark shadow-lg max-h-48 overflow-y-auto">
            <div v-for="m in filteredMembers" :key="m.id"
                 class="w-full text-left px-3 py-2 text-sm hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent cursor-pointer"
                 @click="selectAccessMember(m)">
              {{ m.name ?? m.email }}
            </div>
          </div>
        </div>
        <div>
          <FieldLabel hint class="mb-1">{{ t('quiz.tests.accessClosesAt') }}</FieldLabel>
          <DateTimeInput v-model="accessClosesAt" />
        </div>
        <PrimaryButton :disabled="!accessMemberId" @click="grantAccess">
          {{ t('quiz.tests.grantAccess') }}
        </PrimaryButton>
      </div>
    </NeutralContainer>
  </div>
</template>
