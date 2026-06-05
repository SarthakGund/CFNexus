import axios from "axios";
import { API_BASE_URL } from "@/lib/env";

/**
 * Shared Axios instance. `withCredentials` is required so the Spring Session
 * cookie (SESSION) is sent on every request, keeping the user authenticated.
 */
export const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  // Axios 1.6+ omits the XSRF header on cross-origin requests (localhost:3000 ->
  // localhost:8080) unless this is set, even with withCredentials. Without it the
  // XSRF-TOKEN cookie is never echoed as X-XSRF-TOKEN and Spring rejects every
  // state-changing request with 403.
  withXSRFToken: true,
  headers: {
    "Content-Type": "application/json",
  },
});

export default api;
