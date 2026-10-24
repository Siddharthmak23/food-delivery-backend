export interface User {
  id: string;
  email: string;
  displayName: string;
  phone?: string | null;
  roles: string[];
  createdAt?: string;
}

export interface LoginResult {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: User;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  sort: string;
}

export interface Address {
  id: string;
  label: string;
  recipientName: string;
  line1: string;
  line2: string | null;
  city: string;
  region: string;
  postalCode: string;
  countryCode: string;
  phone: string;
  createdAt: string;
  updatedAt: string;
}

export interface AddressInput extends Omit<Address, "id" | "createdAt" | "updatedAt"> {}

export interface Restaurant {
  id: string;
  name: string;
  description: string | null;
  cuisines: string[];
  address: {
    line1: string;
    line2: string | null;
    city: string;
    region: string;
    postalCode: string;
    countryCode: string;
  };
  status: string;
}

export interface MenuItem {
  id: string;
  restaurantId: string;
  name: string;
  description: string | null;
  category: string;
  price: string;
  currency: string;
  available: boolean;
}

export interface OrderItem {
  menuItemId: string;
  name: string;
  quantity: number;
  unitPrice: string;
  lineTotal: string;
}

export interface Order {
  id: string;
  customerId: string;
  restaurantId: string;
  items: OrderItem[];
  deliveryAddress: Omit<Address, "label" | "createdAt" | "updatedAt">;
  currency: string;
  subtotal: string;
  deliveryFee: string;
  discount: string;
  total: string;
  paymentStatus: string;
  status: string;
  deliveryProvisioningStatus: string;
  deliveryId: string | null;
  cancellationStatus: string;
  cancellationReason: string | null;
  rejectionReason: string | null;
  createdAt: string;
  updatedAt: string;
  statusUpdatedAt: string;
}

export interface DeliveryEvent {
  id: string;
  type: string;
  occurredAt: string;
  actorId: string;
  source: string;
  callbackPending: boolean;
}

export interface Delivery {
  id: string;
  orderId: string;
  restaurantId: string;
  customerId: string;
  pickupAddress: Record<string, string | null>;
  deliveryAddress: Record<string, string | null>;
  partner: { partnerId: string; displayName: string } | null;
  assignedOperatorId?: string | null;
  currency: string;
  status: string;
  cancellationReason: string | null;
  events: DeliveryEvent[];
  orderCallbackPending: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ApiError {
  timestamp?: string;
  status?: number;
  code?: string;
  message?: string;
  fieldErrors?: Array<{ field: string; code: string; message: string }>;
  traceId?: string;
}
