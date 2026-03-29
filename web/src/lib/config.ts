const stripTrailingSlash = (value?: string) =>
  value ? value.replace(/\/+$/, "") : value;

const isHttpsNonLocal = () =>
  typeof window !== 'undefined' &&
  window.location.protocol === 'https:' &&
  !window.location.hostname.includes('localhost');

const getDefaultApiBase = () => {
  // Nếu có VITE_API_URL từ env, dùng nó
  if (import.meta.env.VITE_API_URL) {
    return import.meta.env.VITE_API_URL;
  }
  // Nếu chạy qua Cloudflare Tunnel (HTTPS và không phải localhost), dùng relative path
  // Vite proxy sẽ forward sang backend local
  if (typeof window !== 'undefined' && window.location.protocol === 'https:' && !window.location.hostname.includes('localhost')) {
    return "/api";
  }
  // Dev mode local: dùng relative path, Vite proxy sẽ handle
  return "/api";
};

const getDefaultMediaBase = () => {
  // Nếu có VITE_VIDEO_BASE_URL từ env, dùng nó
  if (import.meta.env.VITE_VIDEO_BASE_URL) {
    return import.meta.env.VITE_VIDEO_BASE_URL;
  }
  // Khi chạy qua Cloudflare Tunnel (HTTPS), luôn fallback localhost backend
  if (isHttpsNonLocal()) {
    return "http://localhost:4000";
  }
  // Dev mode local: dùng localhost backend
  return "http://localhost:4000";
};

export const API_BASE =
  stripTrailingSlash(import.meta.env.VITE_API_URL) ?? getDefaultApiBase();

export const MEDIA_BASE =
  (import.meta.env.VITE_VIDEO_BASE_URL && stripTrailingSlash(import.meta.env.VITE_VIDEO_BASE_URL)) ||
  getDefaultMediaBase();

export const STORAGE_BASE = `${MEDIA_BASE}/storage`;

export const buildApiUrl = (path: string) => {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  return `${API_BASE}${normalized}`;
};

export const apiFetch = (path: string, init?: RequestInit) =>
  fetch(buildApiUrl(path), init);

export const buildMediaUrl = (path?: string) => {
  if (!path) return "";
  // Nếu đã là URL đầy đủ (http/https), return nguyên vẹn
  if (path.startsWith("http")) return path;
  
  const normalized = path.startsWith("/") ? path : `/${path}`;
  
  // Nếu path bắt đầu bằng /storage hoặc /uploads, dùng buildStorageUrl
  if (normalized.startsWith("/storage/") || normalized.startsWith("/uploads/")) {
    return buildStorageUrl(normalized);
  }
  
  const mediaBase = MEDIA_BASE || "http://localhost:4000";
  return `${mediaBase}${normalized}`;
};

export const buildStorageUrl = (filename?: string) => {
  if (!filename) return "";
  // Nếu đã là URL đầy đủ, return nguyên vẹn
  if (filename.startsWith("http")) return filename;
  
  // Clean filename: remove /storage/ prefix and /uploads/ prefix if present
  let clean = filename.replace(/^\/?(storage|uploads)\//, "").split("?")[0];
  
  const storageBase = (STORAGE_BASE || "http://localhost:4000/storage").replace(/\/+$/, "");
  return `${storageBase}/${clean}`;
};

