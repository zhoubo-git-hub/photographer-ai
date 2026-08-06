import { request } from '../http';
import type { PresignRequest, PresignResponse, UploadFileDTO } from '../types/multiterminal';

/**
 * 对象存储上传接口（架构 §3.3 W6–W8）。
 * 默认预签名直传（OSS/COS/S3）；dev LOCAL 时走 upload 后端接收兜底。
 * FormData 由各端构造（Web/RN 原生 FormData；小程序端走 Taro.uploadFile 适配后再调 confirm）。
 */
export const storageApi = {
  /** W6 获取预签名直传地址。 */
  presign: (data: PresignRequest) =>
    request<PresignResponse>({ url: '/storage/presign', method: 'POST', data }),

  /** W7 直传完成后回写记录并返回 CDN url。 */
  confirm: (fileKey: string) =>
    request<UploadFileDTO>({ url: '/storage/confirm', method: 'POST', data: { fileKey } }),

  /** W8 后端接收兜底（LOCAL 存储）：multipart/form-data(file, bizType)。 */
  upload: (form: FormData) =>
    request<UploadFileDTO>({
      url: '/storage/upload',
      method: 'POST',
      data: form,
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
};
