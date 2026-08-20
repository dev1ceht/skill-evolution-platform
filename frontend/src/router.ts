export const routeIds = [
  'home',
  'ingredients',
  'units',
  'dishes',
  'recipes',
  'menus',
  'menu-approval',
  'menu-published',
  'plans',
  'orders',
  'receiving',
  'suppliers',
  'inventory',
  'stockout',
  'ledger',
  'alerts',
  'trace',
  'assistant',
] as const;

export type AppRouteId = typeof routeIds[number];

const routeSet = new Set<string>(routeIds);

export function routeFromLocation(location?: Pick<Location, 'hash'>): AppRouteId {
  const current = location ?? (typeof window === 'undefined' ? { hash: '' } : window.location);
  const candidate = current.hash.replace(/^#\/?/, '').trim();
  return routeSet.has(candidate) ? candidate as AppRouteId : 'home';
}

export function writeRoute(route: AppRouteId): void {
  if (typeof window !== 'undefined') {
    window.location.hash = `/${route}`;
  }
}
