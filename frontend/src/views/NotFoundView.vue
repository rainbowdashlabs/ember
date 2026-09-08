/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NotFoundContent from './notfoundview/NotFoundContent.vue'

const props = withDefaults(defineProps<{status?: number}>(), {status: 404})

const {t} = useI18n()

const missing = computed(() => props.status === 404)
const title = computed(() => missing.value ? t('notFound.title') : t('errorPage.title'))
const text = computed(() => missing.value ? t('notFound.text') : t('errorPage.text'))

useHead({title})
</script>

<template>
  <NotFoundContent :status="props.status" :title="title" :text="text" />
</template>
