import { request } from '../http';
import type {
  ContractTemplate,
  ContractGenerateRequest,
  ContractGenerateResponse,
} from '../types/models';

export const contractApi = {
  templates: () => request<ContractTemplate[]>({ url: '/contract-templates' }),
  generate: (data: ContractGenerateRequest) =>
    request<ContractGenerateResponse>({
      url: '/contracts/generate',
      method: 'POST',
      data,
    }),
};
