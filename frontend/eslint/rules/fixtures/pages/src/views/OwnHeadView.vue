<script setup lang="ts">
const {t} = useI18n()
useHead({title: t('pages.blog.tab')})
</script>

<template>
  <ViewContent :title="t('pages.blog.title')">
    <p>{{ t('pages.blog.intro') }}</p>
  </ViewContent>
</template>
