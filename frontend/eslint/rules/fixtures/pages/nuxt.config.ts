export default defineNuxtConfig({
  routeRules: {
    '/': {ssr: true},
    '/public/**': {ssr: true},
    '/station/**': {ssr: false},
  },
})
