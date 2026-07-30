const isLocalhost4200 = typeof window !== 'undefined' && window.location.port === '4200';
export const BASE_API_URL = isLocalhost4200 ? 'http://localhost:8080/api' : '/api';
export const BASE_URL = isLocalhost4200 ? 'http://localhost:8080' : '';
