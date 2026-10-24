import { describe, expect, it } from "vitest";
import { addItem, clearCart, setQuantity, store } from "./store";
import type { MenuItem } from "./types";

const pasta: MenuItem = {
  id: "item-1",
  restaurantId: "restaurant-1",
  name: "Pasta",
  description: null,
  category: "Main",
  price: "12.50",
  currency: "USD",
  available: true,
};

describe("cart", () => {
  it("adds items, increments repeated items, and removes zero-quantity lines", () => {
    store.dispatch(clearCart());
    store.dispatch(addItem(pasta));
    store.dispatch(addItem(pasta));
    expect(store.getState().cart).toHaveLength(1);
    expect(store.getState().cart[0].quantity).toBe(2);
    store.dispatch(setQuantity({ menuItemId: pasta.id, quantity: 0 }));
    expect(store.getState().cart).toEqual([]);
  });

  it("caps cart quantity at the API limit", () => {
    store.dispatch(clearCart());
    store.dispatch(addItem(pasta));
    store.dispatch(setQuantity({ menuItemId: pasta.id, quantity: 500 }));
    expect(store.getState().cart[0].quantity).toBe(100);
    store.dispatch(clearCart());
  });
});
