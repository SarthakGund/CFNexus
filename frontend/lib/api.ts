import axios from "axios";
import { API_BASE_URL } from "@/lib/env";

/**
 * Shared Axios instance. `withCredentials` is required so the Spring Session
 * cookie (SESSION) is sent on every request, keeping the user authenticated.
 */
export const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

export default api;
