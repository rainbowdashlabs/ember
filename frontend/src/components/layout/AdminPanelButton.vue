/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {useSession} from '@/composables/useSession'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

withDefaults(defineProps<{
  variant?: 'primary' | 'secondary'
}>(), {
  variant: 'secondary',
})

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded, load, isAdmin} = useSession()

onMounted(() => {
  if (!loaded.value) load()
})

const onAdminPanel = computed(() => route.path.startsWith('/admin'))
const visible = computed(() => loaded.value && isAdmin() && !onAdminPanel.value)

function open() {
  router.push('/admin/dashboard/overview')
}
</script>

<template>
  <PrimaryButton v-if="visible && variant === 'primary'" @click="open">
    <font-awesome-icon :icon="['fas', 'shield']" class="h-4 w-4"/>
    <span class="hidden sm:inline ml-1">{{ t('header.adminPanel') }}</span>
  </PrimaryButton>
  <SecondaryButton v-else-if="visible" @click="open">
    <font-awesome-icon :icon="['fas', 'shield']" class="h-4 w-4"/>
    <span class="hidden sm:inline ml-1">{{ t('header.adminPanel') }}</span>
  </SecondaryButton>
</template>
