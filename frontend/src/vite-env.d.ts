/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_AGENT_MENU_ENABLED?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
