export default defineNuxtConfig({
  compatibilityDate: '2025-06-08',

  srcDir: 'src/',

  modules: ['@nuxt/test-utils/module', '@nuxt/eslint', '@nuxtjs/i18n'],

  eslint: {
    config: {
      autoInit: false,
    },
  },

  i18n: {
    restructureDir: 'src',
    langDir: 'i18n',
    vueI18n: 'i18n/config.ts',
    strategy: 'no_prefix',
    defaultLocale: 'de-DE',
    detectBrowserLanguage: false,
    locales: [
      {code: 'de-DE', language: 'de-DE', file: 'de-DE.ts'},
      {code: 'en', language: 'en', file: 'en.ts'},
    ],
  },

  runtimeConfig: {
    // Where the server itself reaches the backend. A server render cannot use the browser's
    // relative `/api/v1`, because on the server there is no origin to resolve it against.
    backendUrl: process.env.NUXT_BACKEND_URL || 'http://localhost:8080',
    // How the content security policy is sent: `enforce` refuses violations, `report` only
    // observes them, `off` sends no policy. Enforced by default; `report` is the way back for an
    // operator whose embedded content the policy turns out to refuse.
    // The end-to-end suite runs enforced and fails a story on any refusal it sees.
    cspMode: process.env.NUXT_CSP_MODE || 'enforce',
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
    '/verify': {ssr: true},
    '/public/**': {ssr: true},
    '/f/**': {ssr: true, headers: {'X-Robots-Tag': 'noindex, nofollow', 'Referrer-Policy': 'no-referrer'}},
    '/s/**': {ssr: true, headers: {'X-Robots-Tag': 'noindex, nofollow', 'Referrer-Policy': 'no-referrer'}},
    '/helpcenter/**': {isr: 3600},
    '/station': {redirect: '/station/dashboard/overview'},
    '/station/**': {ssr: false},
    '/admin/**': {ssr: false},
    '/cluster': {ssr: false},
    '/cluster/**': {ssr: false},
    '/account': {ssr: false},
    '/account/**': {ssr: false},
    '/cross-station': {ssr: false},
    '/reconsent': {ssr: false},
    '/station-select': {ssr: false},
    '/passkey-offer': {ssr: false},
    '/style': {ssr: false},
  },

  nitro: {
    externals: {
      inline: [
        '@fortawesome/fontawesome-svg-core',
        '@fortawesome/free-brands-svg-icons',
        '@fortawesome/free-solid-svg-icons',
        '@fortawesome/vue-fontawesome',
        '@phosphor-icons/vue',
      ],
    },
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
      // with a Host like `frontend:3000` - the compose service name another container in the
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
    /**
     * Keeps the pdf.js worker out of every page's resource hints. It is two megabytes that only a
     * PDF viewer needs, and the viewer asks for it by its address once a document is open; hinted
     * as a prefetch, every page would download it the moment the browser is idle.
     */
    'build:manifest'(manifest: Record<string, { file?: string; prefetch?: boolean; preload?: boolean }>) {
      for (const resource of Object.values(manifest)) {
        if (!resource.file?.includes('pdf.worker')) continue
        resource.prefetch = false
        resource.preload = false
      }
    },
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
