import type {
  Address,
  AddressInput,
  AdminOrder,
  AdminRestaurant,
  ApiError,
  Delivery,
  LoginResult,
  MenuItem,
  Order,
  Page,
  Restaurant,
  User,
} from "./types";

export const API_URLS = {
  user: import.meta.env.VITE_USER_API_URL ?? "http://localhost:8081",
  restaurant: import.meta.env.VITE_RESTAURANT_API_URL ?? "http://localhost:8082",
  order: import.meta.env.VITE_ORDER_API_URL ?? "http://localhost:8083",
  delivery: import.meta.env.VITE_DELIVERY_API_URL ?? "http://localhost:8084",
};

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly fieldErrors?: ApiError["fieldErrors"];
  readonly traceId?: string;
  readonly unauthorizedHandled: boolean;

  constructor(status: number, body: ApiError, unauthorizedHandled = false) {
    super(body.message || `Request failed (${status})`);
    this.name = "ApiRequestError";
    this.status = status;
    this.code = body.code;
    this.fieldErrors = body.fieldErrors;
    this.traceId = body.traceId;
    this.unauthorizedHandled = unauthorizedHandled;
  }
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  token?: string | null;
  body?: unknown;
  onUnauthorized?: () => void;
}

export async function request<T>(
  baseUrl: string,
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const { token, body, onUnauthorized, headers, ...init } = options;
  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });

  const unauthorizedHandled = response.status === 401
    && Boolean(token)
    && !path.startsWith("/api/auth/");
  if (unauthorizedHandled) onUnauthorized?.();
  if (response.status === 204) return undefined as T;

  const raw = await response.text();
  let result: unknown;
  if (raw) {
    try {
      result = JSON.parse(raw);
    } catch {
      result = { message: raw };
    }
  }
  if (!response.ok) {
    throw new ApiRequestError(response.status, (result ?? {}) as ApiError, unauthorizedHandled);
  }
  return result as T;
}

export function createApi(token: string | null, onUnauthorized: () => void) {
  const call = <T>(service: keyof typeof API_URLS, path: string, options: RequestOptions = {}) =>
    request<T>(API_URLS[service], path, { ...options, token, onUnauthorized });
  const query = (params: Record<string, string | number | boolean | undefined>) => {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== "") search.set(key, String(value));
    });
    return search.toString();
  };

  return {
    register: (body: { email: string; password: string; displayName: string }) =>
      call<User>("user", "/api/auth/register", { method: "POST", body }),
    login: (body: { email: string; password: string }) =>
      call<LoginResult>("user", "/api/auth/login", { method: "POST", body }),
    profile: () => call<User>("user", "/api/users/me"),
    updateProfile: (body: { displayName: string; phone: string }) =>
      call<User>("user", "/api/users/me", { method: "PUT", body }),
    listAddresses: () => call<Page<Address>>("user", "/api/users/me/addresses?page=0&size=100"),
    createAddress: (body: AddressInput) =>
      call<Address>("user", "/api/users/me/addresses", { method: "POST", body }),
    updateAddress: (id: string, body: AddressInput) =>
      call<Address>("user", `/api/users/me/addresses/${encodeURIComponent(id)}`, { method: "PUT", body }),
    deleteAddress: (id: string) =>
      call<void>("user", `/api/users/me/addresses/${encodeURIComponent(id)}`, { method: "DELETE" }),
    listRestaurants: (params: { q?: string; cuisine?: string; page?: number }) =>
      call<Page<Restaurant>>("restaurant", `/api/restaurants?${query({ size: 12, sort: "name,asc", ...params })}`),
    myRestaurants: (page = 0) =>
      call<Page<Restaurant>>("restaurant", `/api/restaurants/my-restaurants?${query({ page, size: 20, sort: "name,asc" })}`),
    restaurant: (id: string) =>
      call<Restaurant>("restaurant", `/api/restaurants/${encodeURIComponent(id)}`),
    menu: (id: string) =>
      call<Page<MenuItem>>(
        "restaurant",
        `/api/restaurants/${encodeURIComponent(id)}/menu?${query({ size: 100, available: undefined })}`,
      ),
    createRestaurant: (body: Omit<Restaurant, "id" | "status">) =>
      call<Restaurant>("restaurant", "/api/restaurants", { method: "POST", body }),
    updateRestaurant: (id: string, body: Omit<Restaurant, "id" | "status">) =>
      call<Restaurant>("restaurant", `/api/restaurants/${encodeURIComponent(id)}`, { method: "PUT", body }),
    adminRestaurants: (status = "PENDING_APPROVAL", page = 0) =>
      call<Page<AdminRestaurant>>(
        "restaurant",
        `/api/admin/restaurants?${query({ status, page, size: 20, sort: "name,asc" })}`,
      ),
    updateRestaurantApproval: (id: string, status: "ACTIVE" | "REJECTED") =>
      call<AdminRestaurant>("restaurant", `/api/admin/restaurants/${encodeURIComponent(id)}/approval`, {
        method: "PATCH",
        body: { status },
      }),
    createMenuItem: (id: string, body: Omit<MenuItem, "id" | "restaurantId">) =>
      call<MenuItem>("restaurant", `/api/restaurants/${encodeURIComponent(id)}/menu-items`, {
        method: "POST",
        body,
      }),
    updateMenuItem: (id: string, body: Omit<MenuItem, "id" | "restaurantId">) =>
      call<MenuItem>("restaurant", `/api/menu-items/${encodeURIComponent(id)}`, {
        method: "PUT",
        body,
      }),
    setMenuAvailability: (id: string, available: boolean) =>
      call<MenuItem>("restaurant", `/api/menu-items/${encodeURIComponent(id)}/availability`, {
        method: "PATCH",
        body: { available },
      }),
    createOrder: (body: { restaurantId: string; items: Array<{ menuItemId: string; quantity: number }>; addressId: string }, key: string) =>
      call<Order>("order", "/api/orders", {
        method: "POST",
        headers: { "Idempotency-Key": key },
        body,
      }),
    myOrders: (page = 0) =>
      call<Page<Order>>("order", `/api/orders/my-orders?${query({ page, size: 20 })}`),
    restaurantOrders: (id: string, page = 0) =>
      call<Page<Order>>(
        "order",
        `/api/orders/restaurant/${encodeURIComponent(id)}?${query({ page, size: 50 })}`,
      ),
    cancelOrder: (id: string, reason?: string) =>
      call<Order>("order", `/api/orders/${encodeURIComponent(id)}/cancel`, {
        method: "PATCH",
        body: reason ? { reason } : undefined,
      }),
    updateRestaurantOrder: (id: string, status: string, reason?: string) =>
      call<Order>("order", `/api/orders/${encodeURIComponent(id)}/restaurant-status`, {
        method: "PATCH",
        body: { status, reason },
      }),
    retryProvisioning: (id: string) =>
      call<Order>("order", `/api/orders/${encodeURIComponent(id)}/delivery-provisioning/retry`, {
        method: "POST",
      }),
    adminCancelOrder: (id: string, reason: string) =>
      call<Order>("order", `/api/admin/orders/${encodeURIComponent(id)}/cancel`, {
        method: "PATCH",
        body: { reason },
      }),
    adminOrders: (page = 0) =>
      call<Page<AdminOrder>>("order", `/api/admin/orders?${query({ page, size: 20 })}`),
    myAssignments: (page = 0) =>
      call<Page<Delivery>>("delivery", `/api/deliveries/my-assignments?${query({ page, size: 20 })}`),
    deliveryByOrder: (id: string) =>
      call<Delivery>("delivery", `/api/deliveries/order/${encodeURIComponent(id)}`),
    delivery: (id: string) =>
      call<Delivery>("delivery", `/api/deliveries/${encodeURIComponent(id)}`),
    assignDelivery: (id: string, partnerId: string) =>
      call<Delivery>("delivery", `/api/deliveries/${encodeURIComponent(id)}/assign`, {
        method: "PATCH",
        body: { partnerId },
      }),
    updateDeliveryStatus: (id: string, status: "PICKED_UP" | "DELIVERED") =>
      call<Delivery>("delivery", `/api/deliveries/${encodeURIComponent(id)}/status`, {
        method: "PATCH",
        body: { status },
      }),
    deliveryTimeline: (id: string) =>
      call<{ deliveryId: string; events: Delivery["events"] }>(
        "delivery",
        `/api/deliveries/${encodeURIComponent(id)}/timeline`,
      ),
  };
}

export function tokenExpiry(token: string): number | null {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const parsed = JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "="))) as {
      exp?: unknown;
    };
    return typeof parsed.exp === "number" ? parsed.exp * 1000 : null;
  } catch {
    return null;
  }
}
