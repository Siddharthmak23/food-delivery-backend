import { configureStore, createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { MenuItem } from "./types";

export interface CartLine {
  menuItemId: string;
  restaurantId: string;
  restaurantName: string;
  name: string;
  price: string;
  currency: string;
  quantity: number;
}

const CART_STORAGE_KEY = "foodieexpress.cart";
const PRICE_PATTERN = /^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/;

function readCart(): CartLine[] {
  try {
    const stored = sessionStorage.getItem(CART_STORAGE_KEY);
    if (!stored) return [];
    const parsed: unknown = JSON.parse(stored);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((line): line is CartLine =>
      typeof line === "object" && line !== null
      && typeof line.menuItemId === "string"
      && typeof line.restaurantId === "string"
      && typeof line.name === "string"
      && typeof line.price === "string"
      && PRICE_PATTERN.test(line.price)
      && Number.isInteger(line.quantity)
      && line.quantity >= 1
      && line.quantity <= 100
    ).map((line) => ({
      ...line,
      restaurantName: typeof line.restaurantName === "string" ? line.restaurantName : "Restaurant",
      currency: typeof line.currency === "string" ? line.currency : "UNSUPPORTED",
    }));
  } catch {
    return [];
  }
}

interface AddCartItem {
  item: MenuItem;
  restaurantName: string;
}

const cartSlice = createSlice({
  name: "cart",
  initialState: readCart(),
  reducers: {
    addItem: (state, action: PayloadAction<AddCartItem>) => {
      const { item, restaurantName } = action.payload;
      if (item.currency !== "INR" || !PRICE_PATTERN.test(item.price)) return;
      const line = state.find((candidate) => candidate.menuItemId === item.id);
      if (line) line.quantity = Math.min(100, line.quantity + 1);
      else {
        state.push({
          menuItemId: item.id,
          restaurantId: item.restaurantId,
          restaurantName,
          name: item.name,
          price: item.price,
          currency: item.currency,
          quantity: 1,
        });
      }
    },
    setQuantity: (state, action: PayloadAction<{ menuItemId: string; quantity: number }>) => {
      const line = state.find((candidate) => candidate.menuItemId === action.payload.menuItemId);
      if (!line) return;
      if (!Number.isSafeInteger(action.payload.quantity)) return;
      if (action.payload.quantity <= 0) return state.filter((item) => item.menuItemId !== action.payload.menuItemId);
      line.quantity = Math.min(100, action.payload.quantity);
    },
    removeItem: (state, action: PayloadAction<string>) =>
      state.filter((item) => item.menuItemId !== action.payload),
    clearCart: () => [],
  },
});

export const { addItem, setQuantity, removeItem, clearCart } = cartSlice.actions;
export const store = configureStore({ reducer: { cart: cartSlice.reducer } });
store.subscribe(() => {
  try {
    sessionStorage.setItem(CART_STORAGE_KEY, JSON.stringify(store.getState().cart));
  } catch {
    // Cart remains available in memory if session storage is unavailable.
  }
});
export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
