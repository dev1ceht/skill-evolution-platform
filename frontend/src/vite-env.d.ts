/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_AGENT_MENU_ENABLED?: string;
  readonly VITE_SCHOOL_ID?: string;
  readonly VITE_CANTEEN_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
