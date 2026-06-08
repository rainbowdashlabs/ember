export default defineNuxtConfig({
  compatibilityDate: '2025-06-08',

  srcDir: 'src/',

  modules: [],

  css: ['~/style.css'],

  routeRules: {
    '/': {prerender: true},
    '/login': {ssr: true},
    '/discovery': {ssr: true},
    '/discovery/**': {ssr: true},
    '/public/**': {ssr: true},
    '/helpcenter/**': {isr: 3600},
    '/station/**': {ssr: false},
    '/admin/**': {ssr: false},
    '/api/**': {proxy: 'http://localhost:8080/api/**'},
  },

  nitro: {
    devProxy: {
      '/api': {target: 'http://localhost:8080', changeOrigin: true},
      '/docs': {target: 'http://localhost:8080', changeOrigin: true},
      '/swagger-ui': {target: 'http://localhost:8080', changeOrigin: true},
    },
  },

  vite: {
    optimizeDeps: {
      include: ['echarts', 'vue-echarts'],
    },
    server: {
      proxy: {
        '/api': 'http://localhost:8080',
        '/docs': 'http://localhost:8080',
        '/swagger-ui': 'http://localhost:8080',
      },
    },
  },

  hooks: {
    'vite:extendConfig'(config: { plugins?: unknown[] }) {
      import('@tailwindcss/vite').then(m => {
        config.plugins ||= []
        config.plugins.push(m.default())
      })
    },
  },

  typescript: {
    strict: true,
  },

  components: {
    dirs: [
      {path: '~/components', pathPrefix: false},
    ],
  },
})
