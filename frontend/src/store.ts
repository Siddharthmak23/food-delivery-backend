import { configureStore, createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { MenuItem } from "./types";

export interface CartLine {
  menuItemId: string;
  restaurantId: string;
  name: string;
  price: string;
  quantity: number;
}

const cartSlice = createSlice({
  name: "cart",
  initialState: [] as CartLine[],
  reducers: {
    addItem: (state, action: PayloadAction<MenuItem>) => {
      const item = action.payload;
      const line = state.find((candidate) => candidate.menuItemId === item.id);
      if (line) line.quantity += 1;
      else {
        state.push({
          menuItemId: item.id,
          restaurantId: item.restaurantId,
          name: item.name,
          price: item.price,
          quantity: 1,
        });
      }
    },
    setQuantity: (state, action: PayloadAction<{ menuItemId: string; quantity: number }>) => {
      const line = state.find((candidate) => candidate.menuItemId === action.payload.menuItemId);
      if (!line) return;
      if (action.payload.quantity <= 0) return state.filter((item) => item.menuItemId !== action.payload.menuItemId);
      line.quantity = Math.min(100, action.payload.quantity);
    },
    clearCart: () => [],
  },
});

export const { addItem, setQuantity, clearCart } = cartSlice.actions;
export const store = configureStore({ reducer: { cart: cartSlice.reducer } });
export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
