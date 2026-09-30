<script setup lang="ts">
import {socialMeta} from '@/util/socialMeta'

const {t} = useI18n()

useHead(computed(() => ({
  title: t('pages.blog.title'),
  meta: socialMeta({title: t('pages.blog.title'), description: t('pages.blog.intro')}),
})))
</script>

<template>
  <ViewContent :title="t('pages.blog.title')">
    <p>{{ t('pages.blog.intro') }}</p>
  </ViewContent>
</template>
