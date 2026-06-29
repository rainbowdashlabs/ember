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
    '/sitemap.xml': {proxy: `${process.env.NUXT_BACKEND_URL || 'http://localhost:8080'}/sitemap.xml`},
    '/sitemap-station-**': {proxy: `${process.env.NUXT_BACKEND_URL || 'http://localhost:8080'}/sitemap-station-**`},
    '/api/**': {proxy: `${process.env.NUXT_BACKEND_URL || 'http://localhost:8080'}/api/**`},
  },

  nitro: {
    publicAssets: [
      {dir: '../public', baseURL: '/'},
    ],
    plugins: ['../server/plugins/theme-script.ts'],
    devProxy: {
      '/sitemap.xml': {target: process.env.NUXT_BACKEND_URL || 'http://localhost:8080', changeOrigin: true},
      '/sitemap-station-': {target: process.env.NUXT_BACKEND_URL || 'http://localhost:8080', changeOrigin: true},
      '/api': {target: process.env.NUXT_BACKEND_URL || 'http://localhost:8080', changeOrigin: true},
      '/docs': {target: process.env.NUXT_BACKEND_URL || 'http://localhost:8080', changeOrigin: true},
      '/swagger-ui': {target: process.env.NUXT_BACKEND_URL || 'http://localhost:8080', changeOrigin: true},
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
      // Accept requests from any Host header. Vite 8 otherwise restricts the dev server to
      // localhost and silently holds the socket open without responding when a request arrives
      // with a Host like `frontend:3000` — the compose service name another container in the
      // dev stack uses to reach this Nuxt server (cross-instance station transfer in the
      // `transfer` compose profile).
      allowedHosts: true,
      proxy: {
        '/sitemap.xml': process.env.NUXT_BACKEND_URL || 'http://localhost:8080',
        '/sitemap-station-': process.env.NUXT_BACKEND_URL || 'http://localhost:8080',
        '/api': process.env.NUXT_BACKEND_URL || 'http://localhost:8080',
        '/docs': process.env.NUXT_BACKEND_URL || 'http://localhost:8080',
        '/swagger-ui': process.env.NUXT_BACKEND_URL || 'http://localhost:8080',
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
