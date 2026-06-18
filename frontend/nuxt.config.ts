export default defineNuxtConfig({
  compatibilityDate: '2025-06-08',

  srcDir: 'src/',

  site: {
    url: process.env.NUXT_PUBLIC_SITE_URL || 'http://localhost:3000',
  },

  runtimeConfig: {
    public: {
      googleSiteVerification: process.env.NUXT_PUBLIC_GOOGLE_SITE_VERIFICATION || '',
    },
  },

  css: ['~/style.css'],

  routeRules: {
    '/': {ssr: true},
    '/login': {ssr: true},
    '/discovery': {ssr: true},
    '/discovery/**': {ssr: true},
    '/public/**': {ssr: true},
    '/helpcenter/**': {isr: 3600},
    '/station': {redirect: '/station/dashboard/overview'},
    '/station/**': {ssr: false},
    '/admin/**': {ssr: false},
    '/style': {ssr: false},
    '/sitemap.xml': {proxy: 'http://localhost:8080/sitemap.xml'},
    '/sitemap-station-**': {proxy: 'http://localhost:8080/sitemap-station-**'},
    '/api/**': {proxy: 'http://localhost:8080/api/**'},
  },

  nitro: {
    publicAssets: [
      {dir: '../public', baseURL: '/'},
    ],
    plugins: ['../server/plugins/theme-script.ts'],
    devProxy: {
      '/sitemap.xml': {target: 'http://localhost:8080', changeOrigin: true},
      '/sitemap-station-': {target: 'http://localhost:8080', changeOrigin: true},
      '/api': {target: 'http://localhost:8080', changeOrigin: true},
      '/docs': {target: 'http://localhost:8080', changeOrigin: true},
      '/swagger-ui': {target: 'http://localhost:8080', changeOrigin: true},
    },
  },

  vite: {
    build: {
      sourcemap: false,
    },
    optimizeDeps: {
      include: [
        '@fortawesome/fontawesome-svg-core',
        '@fortawesome/free-brands-svg-icons',
        '@fortawesome/free-solid-svg-icons',
        '@fortawesome/vue-fontawesome',
        '@vue/devtools-core',
        '@vue/devtools-kit',
        'axios',
        'echarts',
        'vue-echarts',
        'vue-i18n',
      ],
    },
    server: {
      proxy: {
        '/sitemap.xml': 'http://localhost:8080',
        '/sitemap-station-': 'http://localhost:8080',
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
