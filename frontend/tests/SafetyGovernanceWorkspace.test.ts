import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import SafetyGovernanceWorkspace from '../src/components/SafetyGovernanceWorkspace.vue';
import type { SmartCanteenApiPort } from '../src/api/smartCanteenApi';

const scope = { schoolId: 'SCHOOL-001', canteenId: 'CANTEEN-001' };

function apiStub(overrides: Partial<SmartCanteenApiPort> = {}): SmartCanteenApiPort {
  return {
    getCurrentLedgerAlert: vi.fn().mockResolvedValue({ cleared: true, missingLedgerCodes: [] }),
    submitMenu: vi.fn().mockResolvedValue({ id: 'MENU-001', status: 'PENDING_APPROVAL' }),
    importMenuRecipe: vi.fn().mockResolvedValue({ menuId: 'MENU-001', requirements: [] }),
    decideMenuApproval: vi.fn().mockResolvedValue({ id: 'MENU-001', status: 'APPROVED' }),
    generateProcurementPlan: vi.fn().mockResolvedValue({ menuId: 'MENU-001', items: [] }),
    receiveInventory: vi.fn().mockResolvedValue({ materialId: 'FLOUR', quantityBase: 1, baseUnit: 'kg' }),
    completeLedgerRecord: vi.fn().mockResolvedValue({ cleared: true, missingLedgerCodes: [] }),
    listLedgerConfigurations: vi.fn().mockResolvedValue([]),
    ensureConfiguredLedgerCycles: vi.fn().mockResolvedValue([]),
    listComplianceRecords: vi.fn().mockResolvedValue([]),
    listCanteenShowcases: vi.fn().mockResolvedValue([]),
    listMealSuspensions: vi.fn().mockResolvedValue([]),
    listSupplierComplaints: vi.fn().mockResolvedValue([]),
    createComplianceRecord: vi.fn().mockResolvedValue({
      id: 'COMPLIANCE-001', category: 'LICENSE', subjectType: 'CANTEEN',
      subjectId: 'CANTEEN-001', subjectName: 'CANTEEN-001', title: '食品经营许可证',
      validFrom: '2026-08-15', validTo: '2027-08-15', attachmentRefs: [],
      status: 'DRAFT', version: 0,
    }),
    ...overrides,
  };
}

describe('SafetyGovernanceWorkspace', () => {
  it('loads all governance queues for the active canteen scope', async () => {
    const api = apiStub();
    const wrapper = mount(SafetyGovernanceWorkspace, { props: { api, scope } });

    await flushPromises();

    expect(api.listLedgerConfigurations).toHaveBeenCalledWith(scope);
    expect(api.ensureConfiguredLedgerCycles).toHaveBeenCalledWith(scope);
    expect(api.listComplianceRecords).toHaveBeenCalledWith(scope);
    expect(wrapper.get('[data-testid="safety-governance-workspace"]').text()).toContain('食品安全合规');
  });

  it('creates a compliance draft from the operator form', async () => {
    const api = apiStub();
    const wrapper = mount(SafetyGovernanceWorkspace, { props: { api, scope } });

    await flushPromises();
    const saveButton = wrapper.findAll('button').find((button) => button.text().includes('保存资质草稿'));
    expect(saveButton).toBeDefined();
    await saveButton!.trigger('click');
    await flushPromises();

    expect(api.createComplianceRecord).toHaveBeenCalledWith(
      expect.objectContaining({ category: 'LICENSE', subjectId: 'CANTEEN-001' }),
      scope,
    );
    expect(wrapper.get('[data-testid="governance-notice"]').text()).toContain('资质档案');
  });
});
