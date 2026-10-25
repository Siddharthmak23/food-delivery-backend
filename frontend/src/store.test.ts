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
  currency: "INR",
  available: true,
};

describe("cart", () => {
  it("adds items, increments repeated items, and removes zero-quantity lines", () => {
    store.dispatch(clearCart());
    store.dispatch(addItem({ item: pasta, restaurantName: "Pasta House" }));
    store.dispatch(addItem({ item: pasta, restaurantName: "Pasta House" }));
    expect(store.getState().cart).toHaveLength(1);
    expect(store.getState().cart[0].quantity).toBe(2);
    expect(store.getState().cart[0].restaurantName).toBe("Pasta House");
    store.dispatch(setQuantity({ menuItemId: pasta.id, quantity: 0 }));
    expect(store.getState().cart).toEqual([]);
  });

  it("caps cart quantity at the API limit", () => {
    store.dispatch(clearCart());
    store.dispatch(addItem({ item: pasta, restaurantName: "Pasta House" }));
    store.dispatch(setQuantity({ menuItemId: pasta.id, quantity: 500 }));
    expect(store.getState().cart[0].quantity).toBe(100);
    store.dispatch(addItem({ item: pasta, restaurantName: "Pasta House" }));
    expect(store.getState().cart[0].quantity).toBe(100);
    store.dispatch(clearCart());
  });

  it("refuses non-INR and malformed prices", () => {
    store.dispatch(clearCart());
    store.dispatch(addItem({
      item: { ...pasta, currency: "USD" },
      restaurantName: "Pasta House",
    }));
    store.dispatch(addItem({
      item: { ...pasta, id: "item-2", price: "12.999" },
      restaurantName: "Pasta House",
    }));
    expect(store.getState().cart).toEqual([]);
  });
});
