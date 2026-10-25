import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { Provider } from "react-redux";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import { clearCart, store } from "./store";

const customer = {
  id: "user-1",
  email: "customer@example.test",
  displayName: "Local Customer",
  roles: ["CUSTOMER"],
};

function page(items: unknown[] = []) {
  return { items, page: 0, size: 12, totalItems: items.length, totalPages: 0, sort: "name,asc" };
}

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function validAddressToken() {
  const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
  return `header.${payload}.signature`;
}

function addressRecord(id: string, values: Record<string, unknown> = {}) {
  return {
    id,
    label: "Home",
    recipientName: "Local Customer",
    line1: "10 Main Road",
    line2: null,
    city: "Mumbai",
    region: "Maharashtra",
    postalCode: "400001",
    countryCode: "IN",
    phone: "+919876543210",
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...values,
  };
}

function restaurantRecord(id = "restaurant-1") {
  return {
    id,
    name: "Saffron Kitchen",
    description: "Fresh local food",
    cuisines: ["Indian"],
    address: {
      line1: "12 Green Park",
      line2: null,
      city: "Ahmedabad",
      region: "Gujarat",
      postalCode: "380001",
      countryCode: "IN",
    },
    status: "ACTIVE",
  };
}

function menuItemRecord() {
  return {
    id: "menu-item-1",
    restaurantId: "restaurant-1",
    name: "Paneer Bowl",
    description: "A fresh bowl",
    category: "Main",
    price: "12.50",
    currency: "INR",
    available: true,
  };
}

afterEach(() => {
  cleanup();
  sessionStorage.clear();
  window.history.replaceState({}, "", "/#/restaurants");
  store.dispatch(clearCart());
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe("FoodieExpress UI", () => {
  async function beginRegistration() {
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    const dialog = screen.getByRole("dialog", { name: "Sign in or register" });
    fireEvent.click(within(dialog).getByRole("button", { name: "New to FoodieExpress? Create an account" }));
    return dialog;
  }

  async function signIn(user: typeof customer) {
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    const dialog = screen.getByRole("dialog", { name: "Sign in or register" });
    fireEvent.change(within(dialog).getByLabelText("Email"), { target: { value: user.email } });
    fireEvent.change(within(dialog).getByLabelText("Password"), { target: { value: "correct-horse-battery" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Sign in" }));
    await screen.findByText(`Hi, ${user.displayName.split(" ")[0]}`);
  }

  function fillRegistration(dialog: HTMLElement, password = "correct-horse-battery") {
    fireEvent.change(within(dialog).getByLabelText("Your name"), { target: { value: "Local Customer" } });
    fireEvent.change(within(dialog).getByLabelText("Email"), { target: { value: "new@example.test" } });
    fireEvent.change(within(dialog).getByLabelText("Password"), { target: { value: password } });
  }

  it("renders restaurants returned by the public listing endpoint", async () => {
    const fetchMock = vi.fn((input: string | URL | Request) => {
      const url = String(input);
      return Promise.resolve(url.includes("/api/restaurants?")
        ? response(page([restaurantRecord()]))
        : response(page()));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);

    expect(await screen.findByRole("button", { name: /Saffron Kitchen/ })).toBeInTheDocument();
    expect(screen.getByText("1 kitchens")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8082/api/restaurants?size=12&sort=name%2Casc&page=0",
      expect.anything(),
    );
  });

  it("opens an empty cart and prevents checkout until an item is added", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response(page())));
    render(<Provider store={store}><App /></Provider>);

    await screen.findByText("No kitchens found");
    fireEvent.click(screen.getByRole("button", { name: "Cart" }));

    expect(await screen.findByRole("heading", { name: "Your cart" })).toBeInTheDocument();
    expect(screen.getByText("Your cart is empty")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Proceed to Checkout" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Continue shopping" }));
    expect(window.location.hash).toBe("#/restaurants");
  });

  it("updates the INR cart, removes items, and submits a server-priced checkout", async () => {
    const restaurant = restaurantRecord();
    const menuItem = menuItemRecord();
    const validOrder = {
      id: "order-created",
      customerId: customer.id,
      restaurantId: restaurant.id,
      items: [{ menuItemId: menuItem.id, name: menuItem.name, quantity: 1, unitPrice: "12.50", lineTotal: "12.50" }],
      deliveryAddress: addressRecord("address-1"),
      currency: "INR",
      subtotal: "12.50",
      deliveryFee: "0.00",
      discount: "0.00",
      total: "12.50",
      paymentStatus: "SIMULATED_UNPAID",
      status: "PLACED",
      deliveryProvisioningStatus: "NOT_REQUESTED",
      deliveryId: null,
      cancellationStatus: "NONE",
      cancellationReason: null,
      rejectionReason: null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      statusUpdatedAt: new Date().toISOString(),
    };
    const fetchMock = vi.fn((input: string | URL | Request, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page([restaurant])));
      if (url.endsWith("/api/auth/login")) return Promise.resolve(response({
        accessToken: validAddressToken(),
        tokenType: "Bearer",
        expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
        user: customer,
      }));
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(customer));
      if (url.includes("/api/users/me/addresses")) return Promise.resolve(response(page([
        addressRecord("address-1"),
      ])));
      if (url.endsWith(`/api/restaurants/${restaurant.id}`)) return Promise.resolve(response(restaurant));
      if (url.includes(`/api/restaurants/${restaurant.id}/menu`)) return Promise.resolve(response(page([menuItem])));
      if (url.endsWith("/api/orders") && init?.method === "POST") return Promise.resolve(response(validOrder, 201));
      if (url.includes("/api/orders/my-orders")) return Promise.resolve(response(page([validOrder])));
      return Promise.resolve(response({ message: `Unexpected test request: ${url}` }, 404));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);
    expect(await screen.findByRole("button", { name: /Saffron Kitchen/ })).toBeInTheDocument();
    await signIn(customer);
    fireEvent.click(screen.getByRole("button", { name: /Saffron Kitchen/ }));
    fireEvent.click(await screen.findByRole("button", { name: "Add +" }));
    fireEvent.click(screen.getByRole("button", { name: "Add +" }));

    fireEvent.click(screen.getByRole("button", { name: "Cart, 2 items" }));
    expect(await screen.findByRole("heading", { name: "2 items" })).toBeInTheDocument();
    expect(screen.getAllByText("₹25.00")).toHaveLength(2);
    fireEvent.click(screen.getByRole("button", { name: "Add one Paneer Bowl" }));
    expect(screen.getByRole("button", { name: "Cart, 3 items" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Remove one Paneer Bowl" }));
    expect(screen.getByRole("button", { name: "Cart, 2 items" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Remove" }));
    expect(await screen.findByText("Your cart is empty")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Continue shopping" }));
    fireEvent.click(await screen.findByRole("button", { name: /Saffron Kitchen/ }));
    fireEvent.click(await screen.findByRole("button", { name: "Add +" }));
    fireEvent.click(screen.getByRole("button", { name: /Cart/ }));
    fireEvent.click(await screen.findByRole("button", { name: "Proceed to Checkout" }));
    expect(await screen.findByRole("option", { name: /Home/ })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Place order" }));

    expect(await screen.findByText(/Order placed/)).toBeInTheDocument();
    const checkout = fetchMock.mock.calls.find(([url, init]) =>
      String(url).endsWith("/api/orders") && init?.method === "POST");
    expect(JSON.parse(String(checkout?.[1]?.body))).toEqual({
      restaurantId: restaurant.id,
      items: [{ menuItemId: menuItem.id, quantity: 1 }],
      addressId: "address-1",
    });
    expect(new Headers(checkout?.[1]?.headers).get("Idempotency-Key")).toBeTruthy();
    expect(window.location.hash).toBe("#/orders");
  });

  it("shows an empty state for a successful restaurant response with no records", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response(page())));
    render(<Provider store={store}><App /></Provider>);

    expect(await screen.findByText("No kitchens found")).toBeInTheDocument();
    expect(screen.queryByText("Restaurants are unavailable")).not.toBeInTheDocument();
  });

  it("shows listing failures distinctly from empty results and supports retry", async () => {
    let listingAttempts = 0;
    const fetchMock = vi.fn((input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) {
        listingAttempts += 1;
        return Promise.resolve(listingAttempts === 1
          ? response({ code: "DEPENDENCY_UNAVAILABLE", message: "Restaurant data is unavailable" }, 503)
          : response(page([restaurantRecord()])));
      }
      return Promise.resolve(response(page()));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);

    expect(await screen.findByText("Restaurants are unavailable")).toBeInTheDocument();
    expect(screen.getAllByText("Restaurant data is unavailable")).toHaveLength(2);
    expect(screen.queryByText("No kitchens found")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByRole("button", { name: /Saffron Kitchen/ })).toBeInTheDocument();
    expect(listingAttempts).toBe(2);
  });

  it("opens registration from sign-in and explains customer-only public signup", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response(page())));
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    expect(screen.queryByRole("button", { name: "Profile" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    const dialog = screen.getByRole("dialog", { name: "Sign in or register" });
    expect(within(dialog).getByText("Sign in for the good stuff.")).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole("button", { name: "New to FoodieExpress? Create an account" }));
    expect(within(dialog).getByText("A fresh start tastes good.")).toBeInTheDocument();
    expect(within(dialog).getByText(/always receive the CUSTOMER role/)).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Password")).toHaveAttribute("minlength", "12");
    expect(within(dialog).getByRole("button", { name: "Close sign in" })).toBeInTheDocument();
    expect(within(dialog).queryByText(/Passwords are sent only to the User Service/)).not.toBeInTheDocument();
    expect(screen.getByRole("contentinfo").querySelector(".footer-brand img")).toHaveAttribute("src", "/favicon.svg");
  });

  it("closes the registration dialog and auto-dismisses a success toast after account creation", async () => {
    vi.stubGlobal("fetch", vi.fn((input: string | URL | Request) => {
      return Promise.resolve(String(input).endsWith("/api/auth/register")
        ? response(customer, 201)
        : response(page()));
    }));
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    const dialog = await beginRegistration();
    fillRegistration(dialog);
    fireEvent.click(within(dialog).getByRole("button", { name: "Create customer account" }));

    const successToast = await screen.findByRole("status");
    expect(successToast).toHaveTextContent("Customer account created successfully.");
    expect(screen.queryByRole("dialog", { name: "Sign in or register" })).not.toBeInTheDocument();

    await waitFor(() => expect(screen.queryByText(/Customer account created successfully/)).not.toBeInTheDocument(), {
      timeout: 4500,
    });
  }, 10000);

  it("shows duplicate-email registration errors in an error toast without closing the dialog", async () => {
    vi.stubGlobal("fetch", vi.fn((input: string | URL | Request) => {
      return Promise.resolve(String(input).endsWith("/api/auth/register")
        ? response({ message: "An account with this email already exists." }, 409)
        : response(page()));
    }));
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    const dialog = await beginRegistration();
    fillRegistration(dialog);
    fireEvent.click(within(dialog).getByRole("button", { name: "Create customer account" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("An account with this email already exists.");
    expect(screen.getByRole("dialog", { name: "Sign in or register" })).toBeInTheDocument();
    expect(document.querySelector(".toast-error")).toBeInTheDocument();
  });

  it("shows backend credential errors once without an expired-session notification", async () => {
    const fetchMock = vi.fn((input: string | URL | Request) => {
      return Promise.resolve(String(input).endsWith("/api/auth/login")
        ? response({
            message: "Email or password is invalid",
            code: "INVALID_CREDENTIALS",
            traceId: "28fef1a3-2f84-4ad3-9c58-6d26acc1599d",
          }, 401)
        : response(page()));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    const dialog = screen.getByRole("dialog", { name: "Sign in or register" });
    fireEvent.change(within(dialog).getByLabelText("Email"), { target: { value: customer.email } });
    fireEvent.change(within(dialog).getByLabelText("Password"), { target: { value: "wrong-password" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Email or password is invalid");
    expect(screen.queryByText(/reference|28fef1a3/i)).not.toBeInTheDocument();
    expect(screen.getAllByRole("alert")).toHaveLength(1);
    expect(screen.queryByText(/session (?:expired|is no longer valid)/i)).not.toBeInTheDocument();
    expect(screen.getByRole("dialog", { name: "Sign in or register" })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("shows password validation as a warning toast and does not call the registration API", async () => {
    const fetchMock = vi.fn().mockResolvedValue(response(page()));
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    const dialog = await beginRegistration();
    fillRegistration(dialog, "short");
    fireEvent.click(within(dialog).getByRole("button", { name: "Create customer account" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Password must be at least 12 characters.");
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining("/api/auth/register"), expect.anything());
    expect(screen.getByRole("dialog", { name: "Sign in or register" })).toBeInTheDocument();
  });

  it("creates, edits, cancels, reloads, and deletes a saved Indian address", async () => {
    sessionStorage.setItem("foodieexpress.accessToken", validAddressToken());
    window.history.replaceState({}, "", "/#/addresses");
    let addresses: ReturnType<typeof addressRecord>[] = [];
    const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return response(page());
      if (url.endsWith("/api/users/me")) return response(customer);
      if (url.includes("/api/users/me/addresses")) {
        if (init?.method === "POST") {
          const created = addressRecord("address-1", JSON.parse(String(init.body)) as Record<string, unknown>);
          addresses = [created];
          return response(created, 201);
        }
        if (init?.method === "PUT") {
          const updated = addressRecord("address-1", JSON.parse(String(init.body)) as Record<string, unknown>);
          addresses = [updated];
          return response(updated);
        }
        if (init?.method === "DELETE") {
          addresses = [];
          return new Response(null, { status: 204 });
        }
        return response({ ...page(addresses), totalItems: addresses.length });
      }
      return response({ message: "Unexpected test request" }, 404);
    });

    vi.stubGlobal("fetch", fetchMock);
    const renderAddresses = () => render(<Provider store={store}><App /></Provider>);
    renderAddresses();
    await screen.findByRole("heading", { name: "Saved addresses" });
    await waitFor(() => expect(screen.getByRole("button", { name: "Add Address" })).toBeEnabled());

    fireEvent.click(screen.getByRole("button", { name: "Add Address" }));
    let dialog = screen.getByRole("dialog", { name: "Add Address" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save Address" }));
    expect(await within(dialog).findByText("Enter the recipient name.")).toBeInTheDocument();
    expect(within(dialog).getByText("Choose an Indian state or union territory.")).toBeInTheDocument();
    expect(within(dialog).getByText("Enter a six-digit Indian PIN code.")).toBeInTheDocument();
    expect(within(dialog).getByText("Enter a valid 10-digit Indian mobile number.")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining("/api/users/me/addresses"), expect.objectContaining({ method: "POST" }));

    fireEvent.change(within(dialog).getByLabelText("Address label"), { target: { value: "Home" } });
    fireEvent.change(within(dialog).getByLabelText("Recipient name"), { target: { value: "Local Customer" } });
    fireEvent.change(within(dialog).getByLabelText("Indian mobile number"), { target: { value: "12345" } });
    fireEvent.change(within(dialog).getByLabelText("Street address"), { target: { value: "10 Main Road" } });
    fireEvent.change(within(dialog).getByLabelText("City"), { target: { value: "Mumbai" } });
    fireEvent.change(within(dialog).getByLabelText("State / union territory"), { target: { value: "California" } });
    fireEvent.change(within(dialog).getByLabelText("PIN code"), { target: { value: "40001" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save Address" }));
    expect(await within(dialog).findByText("Choose an Indian state or union territory.")).toBeInTheDocument();
    expect(within(dialog).getByText("Enter a six-digit Indian PIN code.")).toBeInTheDocument();
    expect(within(dialog).getByText("Enter a valid 10-digit Indian mobile number.")).toBeInTheDocument();
    expect(addresses).toHaveLength(0);

    fireEvent.change(within(dialog).getByLabelText("Indian mobile number"), { target: { value: "9876543210" } });
    fireEvent.change(within(dialog).getByLabelText("State / union territory"), { target: { value: "Maharashtra" } });
    fireEvent.change(within(dialog).getByLabelText("PIN code"), { target: { value: "400001" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save Address" }));
    expect(await screen.findByText("Address saved successfully.")).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: "Add Address" })).not.toBeInTheDocument();
    expect(screen.getByText(/Mumbai/)).toBeInTheDocument();
    const createCall = fetchMock.mock.calls.find(([, init]) => init?.method === "POST");
    expect(JSON.parse(String(createCall?.[1]?.body))).toMatchObject({
      countryCode: "IN",
      region: "Maharashtra",
      postalCode: "400001",
      phone: "+919876543210",
    });

    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    dialog = screen.getByRole("dialog", { name: "Edit address" });
    fireEvent.change(within(dialog).getByLabelText("City"), { target: { value: "Pune" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
    expect(screen.queryByRole("dialog", { name: "Edit address" })).not.toBeInTheDocument();
    expect(screen.getByText(/Mumbai/)).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "PUT")).toBe(false);

    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    dialog = screen.getByRole("dialog", { name: "Edit address" });
    const phoneField = within(dialog).getByLabelText("Indian mobile number");
    fireEvent.change(phoneField, { target: { value: "987654321" } });
    expect(within(dialog).queryByText("Enter a valid 10-digit Indian mobile number.")).not.toBeInTheDocument();
    fireEvent.blur(phoneField);
    expect(await within(dialog).findByText("Enter a valid 10-digit Indian mobile number.")).toBeInTheDocument();
    for (const invalidPhone of ["98765432101", "98765abc10", "+1 9876543210", "+91 98765"]) {
      fireEvent.change(phoneField, { target: { value: invalidPhone } });
      expect(within(dialog).getByText("Enter a valid 10-digit Indian mobile number.")).toBeInTheDocument();
    }
    fireEvent.change(phoneField, { target: { value: "+919876543210" } });
    expect(within(dialog).queryByText("Enter a valid 10-digit Indian mobile number.")).not.toBeInTheDocument();
    fireEvent.change(phoneField, { target: { value: "+91 98765-43210" } });
    fireEvent.change(within(dialog).getByLabelText("City"), { target: { value: "Pune" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save Address" }));
    expect(await screen.findByText("Address updated successfully.")).toBeInTheDocument();
    expect(screen.getByText(/Pune/)).toBeInTheDocument();
    const updateCall = fetchMock.mock.calls.find(([, init]) => init?.method === "PUT");
    expect(updateCall?.[0]).toBe("http://localhost:8081/api/users/me/addresses/address-1");
    expect(updateCall?.[1]?.headers).toMatchObject({ "Content-Type": "application/json" });
    expect(JSON.parse(String(updateCall?.[1]?.body))).toEqual({
      label: "Home",
      recipientName: "Local Customer",
      line1: "10 Main Road",
      line2: "",
      city: "Pune",
      region: "Maharashtra",
      postalCode: "400001",
      countryCode: "IN",
      phone: "+919876543210",
    });

    cleanup();
    renderAddresses();
    expect(await screen.findByText(/Pune/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    dialog = screen.getByRole("dialog", { name: "Edit address" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Delete Address" }));
    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Edit address" })).not.toBeInTheDocument());
    expect(await screen.findByText("No addresses yet")).toBeInTheDocument();
    expect(addresses).toHaveLength(0);
  });

  it("keeps the address form usable after duplicate responses and allows retrying a distinct location", async () => {
    sessionStorage.setItem("foodieexpress.accessToken", validAddressToken());
    window.history.replaceState({}, "", "/#/addresses");
    let addresses = [addressRecord("address-1", {
      line1: "12, Green Park",
      line2: null,
      city: "Ahmedabad",
      region: "Gujarat",
      postalCode: "380001",
    })];
    let createAttempts = 0;
    const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return response(page());
      if (url.endsWith("/api/users/me")) return response(customer);
      if (url.includes("/api/users/me/addresses")) {
        if (init?.method === "POST") {
          createAttempts += 1;
          if (createAttempts < 3) {
            return response({
              code: "DUPLICATE_ADDRESS",
              message: "This delivery address is already saved.",
            }, 409);
          }
          const created = addressRecord(`address-${addresses.length + 1}`,
            JSON.parse(String(init.body)) as Record<string, unknown>);
          addresses = [...addresses, created];
          return response(created, 201);
        }
        if (init?.method === "PUT") {
          const updated = addressRecord("address-1", JSON.parse(String(init.body)) as Record<string, unknown>);
          addresses = addresses.map((address) => address.id === "address-1" ? updated : address);
          return response(updated);
        }
        return response({ ...page(addresses), totalItems: addresses.length });
      }
      return response({ message: "Unexpected test request" }, 404);
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);
    await screen.findByRole("heading", { name: "Saved addresses" });

    const fillAddress = (dialog: HTMLElement, line1: string, recipientName: string) => {
      fireEvent.change(within(dialog).getByLabelText("Address label"), { target: { value: "Home" } });
      fireEvent.change(within(dialog).getByLabelText("Recipient name"), { target: { value: recipientName } });
      fireEvent.change(within(dialog).getByLabelText("Indian mobile number"), { target: { value: "9876543210" } });
      fireEvent.change(within(dialog).getByLabelText("Street address"), { target: { value: line1 } });
      fireEvent.change(within(dialog).getByLabelText("City"), { target: { value: "Ahmedabad" } });
      fireEvent.change(within(dialog).getByLabelText("State / union territory"), { target: { value: "Gujarat" } });
      fireEvent.change(within(dialog).getByLabelText("PIN code"), { target: { value: "380001" } });
    };

    const submitNewAddress = async (line1: string, recipientName: string) => {
      fireEvent.click(screen.getByRole("button", { name: "Add Address" }));
      const dialog = screen.getByRole("dialog", { name: "Add Address" });
      fillAddress(dialog, line1, recipientName);
      fireEvent.click(within(dialog).getByRole("button", { name: "Save Address" }));
      return dialog;
    };

    const dialog = await submitNewAddress(" 12,   GREEN PARK ", "First Recipient");
    expect(await screen.findByRole("alert")).toHaveTextContent("This delivery address is already saved.");
    expect(dialog).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Recipient name")).toHaveValue("First Recipient");
    expect(within(dialog).getByLabelText("Street address")).toHaveValue(" 12,   GREEN PARK ");
    await waitFor(() => expect(within(dialog).getByRole("button", { name: "Save Address" })).toBeEnabled());

    fireEvent.change(within(dialog).getByLabelText("Recipient name"), { target: { value: "Second Recipient" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save Address" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("This delivery address is already saved.");
    expect(within(dialog).getByLabelText("Recipient name")).toHaveValue("Second Recipient");
    await waitFor(() => expect(within(dialog).getByRole("button", { name: "Save Address" })).toBeEnabled());
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === "POST")).toHaveLength(2);

    fireEvent.change(within(dialog).getByLabelText("Street address"), { target: { value: "24, Green Park" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Save Address" }));
    expect(await screen.findByText("Address saved successfully.")).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: "Add Address" })).not.toBeInTheDocument();
    expect(await screen.findByText(/24, Green Park/)).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === "POST")).toHaveLength(3);
    expect(addresses).toHaveLength(2);
  });

  it.each(["400", "network"] as const)(
    "resets submit state and preserves address form after a %s failure",
    async (failure) => {
      sessionStorage.setItem("foodieexpress.accessToken", validAddressToken());
      window.history.replaceState({}, "", "/#/addresses");
      let postAttempts = 0;
      const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
        const url = String(input);
        if (url.includes("/api/restaurants?")) return response(page());
        if (url.endsWith("/api/users/me")) return response(customer);
        if (url.includes("/api/users/me/addresses") && init?.method === "POST") {
          postAttempts += 1;
          if (postAttempts === 1) {
            if (failure === "network") throw new Error("Network unavailable");
            return response({ message: "Request validation failed" }, 400);
          }
          return response(addressRecord("address-1", JSON.parse(String(init.body)) as Record<string, unknown>), 201);
        }
        if (url.includes("/api/users/me/addresses")) return response(page([]));
        return response({ message: "Unexpected test request" }, 404);
      });
      vi.stubGlobal("fetch", fetchMock);
      render(<Provider store={store}><App /></Provider>);
      await screen.findByRole("heading", { name: "Saved addresses" });
      fireEvent.click(screen.getByRole("button", { name: "Add Address" }));
      let dialog = screen.getByRole("dialog", { name: "Add Address" });
      fireEvent.change(within(dialog).getByLabelText("Address label"), { target: { value: "Home" } });
      fireEvent.change(within(dialog).getByLabelText("Recipient name"), { target: { value: "Kept Recipient" } });
      fireEvent.change(within(dialog).getByLabelText("Indian mobile number"), { target: { value: "9876543210" } });
      fireEvent.change(within(dialog).getByLabelText("Street address"), { target: { value: "24 Green Park" } });
      fireEvent.change(within(dialog).getByLabelText("City"), { target: { value: "Ahmedabad" } });
      fireEvent.change(within(dialog).getByLabelText("State / union territory"), { target: { value: "Gujarat" } });
      fireEvent.change(within(dialog).getByLabelText("PIN code"), { target: { value: "380001" } });
      fireEvent.click(within(dialog).getByRole("button", { name: "Save Address" }));

      expect(await screen.findByRole("alert")).toHaveTextContent(
        failure === "network" ? "Network unavailable" : "Request validation failed",
      );
      expect(within(dialog).getByLabelText("Recipient name")).toHaveValue("Kept Recipient");
      await waitFor(() => expect(within(dialog).getByRole("button", { name: "Save Address" })).toBeEnabled());
      expect(within(dialog).queryByRole("button", { name: "Saving…" })).not.toBeInTheDocument();

      fireEvent.click(within(dialog).getByRole("button", { name: "Save Address" }));
      expect(await screen.findByText("Address saved successfully.")).toBeInTheDocument();
      expect(screen.queryByRole("dialog", { name: "Add Address" })).not.toBeInTheDocument();
      expect(postAttempts).toBe(2);
    },
  );

  it("shows authenticated customer profile and provides saved-address navigation", async () => {
    sessionStorage.setItem("foodieexpress.accessToken", validAddressToken());
    window.history.replaceState({}, "", "/#/restaurants");
    let profileCalls = 0;
    let resolveProfile: ((value: Response) => void) | undefined;
    const fetchMock = vi.fn((input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/users/me/addresses?page=0&size=100")) return Promise.resolve(response(page([])));
      if (url.endsWith("/api/users/me")) {
        profileCalls += 1;
        if (profileCalls === 1) return Promise.resolve(response(customer));
        return new Promise<Response>((resolve) => { resolveProfile = resolve; });
      }
      return Promise.resolve(response({ message: "Unexpected test request" }, 404));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    fireEvent.click(await screen.findByRole("button", { name: "Profile" }));

    expect(await screen.findByText("Loading your profile")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/users\/me$/),
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: expect.stringMatching(/^Bearer /) }) }),
    );
    resolveProfile?.(response({ ...customer, phone: "+919876543210" }));
    expect(await screen.findByRole("heading", { name: "Your profile" })).toBeInTheDocument();
    expect(screen.getByText("customer@example.test")).toBeInTheDocument();
    expect(screen.getByText("+919876543210")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Manage saved addresses" }));
    expect(await screen.findByRole("heading", { name: "Saved addresses" })).toBeInTheDocument();
    expect(await screen.findByText("No addresses yet")).toBeInTheDocument();
  });

  it("edits profile fields, cancels changes, and persists updates from the backend", async () => {
    sessionStorage.setItem("foodieexpress.accessToken", validAddressToken());
    window.history.replaceState({}, "", "/#/profile");
    let savedProfile = { ...customer, phone: "9876543210" };
    const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return response(page());
      if (url.endsWith("/api/users/me")) {
        if (init?.method === "PUT") {
          const body = JSON.parse(String(init.body)) as { displayName: string; phone: string };
          savedProfile = { ...savedProfile, ...body };
        }
        return response(savedProfile);
      }
      return response(page([]));
    });
    vi.stubGlobal("fetch", fetchMock);
    const renderProfile = () => render(<Provider store={store}><App /></Provider>);
    renderProfile();
    expect(await screen.findByRole("heading", { name: "Your profile" })).toBeInTheDocument();
    await screen.findByRole("button", { name: "Edit Profile" });
    fireEvent.click(screen.getByRole("button", { name: "Edit Profile" }));
    const name = screen.getByLabelText("Name");
    const email = screen.getByLabelText("Email");
    const phone = screen.getByLabelText("Phone (optional)");
    expect(name).toHaveValue("Local Customer");
    expect(email).toHaveValue("customer@example.test");
    expect(email).toHaveAttribute("readonly");
    expect(phone).toHaveValue("9876543210");

    fireEvent.change(name, { target: { value: "Unsaved Name" } });
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    fireEvent.click(screen.getByRole("button", { name: "Edit Profile" }));
    expect(screen.getByLabelText("Name")).toHaveValue("Local Customer");

    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Updated Customer" } });
    fireEvent.change(screen.getByLabelText("Phone (optional)"), { target: { value: "+91 98765-43210" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));
    expect(await screen.findByText("Profile updated successfully.")).toBeInTheDocument();
    expect(screen.getByText("Updated Customer")).toBeInTheDocument();
    expect(screen.getByText("+919876543210")).toBeInTheDocument();
    const updateCall = fetchMock.mock.calls.find(([, init]) => init?.method === "PUT");
    expect(updateCall?.[0]).toMatch(/\/api\/users\/me$/);
    expect(JSON.parse(String(updateCall?.[1]?.body))).toEqual({
      displayName: "Updated Customer",
      phone: "+919876543210",
    });

    cleanup();
    renderProfile();
    expect(await screen.findByText("Updated Customer")).toBeInTheDocument();
  });

  it("validates profile input and preserves edits when the backend rejects the update", async () => {
    sessionStorage.setItem("foodieexpress.accessToken", validAddressToken());
    window.history.replaceState({}, "", "/#/profile");
    const fetchMock = vi.fn((input: string | URL | Request, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/users/me") && init?.method === "PUT") {
        return Promise.resolve(response({ message: "Profile update failed." }, 400));
      }
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(customer));
      return Promise.resolve(response(page([])));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);
    await screen.findByRole("heading", { name: "Your profile" });
    await screen.findByRole("button", { name: "Edit Profile" });
    fireEvent.click(screen.getByRole("button", { name: "Edit Profile" }));
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));
    expect(await screen.findByText("Enter your name.")).toBeInTheDocument();
    expect(await screen.findByRole("alert")).toHaveTextContent("Please correct the highlighted profile fields.");
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "PUT")).toBe(false);

    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Kept Until Saved" } });
    fireEvent.change(screen.getByLabelText("Phone (optional)"), { target: { value: "letters!" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));
    expect(await screen.findByText(/valid Indian mobile number/)).toBeInTheDocument();
    expect(screen.getByLabelText("Name")).toHaveValue("Kept Until Saved");
    fireEvent.change(screen.getByLabelText("Phone (optional)"), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));
    expect(await screen.findByText("Profile update failed.")).toBeInTheDocument();
    expect(screen.getByLabelText("Name")).toHaveValue("Kept Until Saved");
    expect(screen.getByRole("button", { name: "Save Changes" })).toBeInTheDocument();
  });

  it("rejects invalid profile phones and accepts valid national or +91 forms", async () => {
    sessionStorage.setItem("foodieexpress.accessToken", validAddressToken());
    window.history.replaceState({}, "", "/#/profile");
    const fetchMock = vi.fn((input: string | URL | Request, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/users/me") && init?.method === "PUT") return Promise.resolve(response(customer));
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(customer));
      return Promise.resolve(response(page([])));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);
    await screen.findByRole("heading", { name: "Your profile" });
    await screen.findByRole("button", { name: "Edit Profile" });
    fireEvent.click(screen.getByRole("button", { name: "Edit Profile" }));
    const phone = screen.getByLabelText("Phone (optional)");
    expect(phone).toHaveAttribute("inputmode", "tel");
    expect(phone).toHaveAttribute("type", "tel");
    fireEvent.change(phone, { target: { value: "987" } });
    expect(screen.queryByText(/valid Indian mobile number/)).not.toBeInTheDocument();
    fireEvent.blur(phone);
    expect(await screen.findByText(/valid Indian mobile number/)).toBeInTheDocument();

    for (const invalid of [
      "333333333333", "3333333333", "1234567890", "987654321", "98765432101", "98765abc10",
    ]) {
      fireEvent.change(phone, { target: { value: invalid } });
      fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));
      expect(screen.getByText(/valid Indian mobile number/)).toBeInTheDocument();
    }
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === "PUT")).toHaveLength(0);

    fireEvent.change(phone, { target: { value: "9876543210" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));
    expect(await screen.findByText("Profile updated successfully.")).toBeInTheDocument();
    const updateCall = fetchMock.mock.calls.find(([, init]) => init?.method === "PUT");
    expect(JSON.parse(String(updateCall?.[1]?.body))).toMatchObject({ phone: "+919876543210" });

    fireEvent.click(screen.getByRole("button", { name: "Edit Profile" }));
    fireEvent.change(screen.getByLabelText("Phone (optional)"), { target: { value: "+919876543210" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));
    await screen.findByText("Profile updated successfully.");
    const updateCalls = fetchMock.mock.calls.filter(([, init]) => init?.method === "PUT");
    expect(JSON.parse(String(updateCalls[1]?.[1]?.body))).toMatchObject({ phone: "+919876543210" });
  });

  it("prevents duplicate profile submissions while a save is in progress", async () => {
    sessionStorage.setItem("foodieexpress.accessToken", validAddressToken());
    window.history.replaceState({}, "", "/#/profile");
    let finishSave: ((value: Response) => void) | undefined;
    const fetchMock = vi.fn((input: string | URL | Request, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/users/me") && init?.method === "PUT") {
        return new Promise<Response>((resolve) => { finishSave = resolve; });
      }
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(customer));
      return Promise.resolve(response(page([])));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);
    fireEvent.click(await screen.findByRole("button", { name: "Edit Profile" }));
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Once Only" } });
    fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));
    expect(await screen.findByRole("button", { name: "Saving…" })).toBeDisabled();
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === "PUT")).toHaveLength(1);
    finishSave?.(response({ ...customer, displayName: "Once Only" }));
    expect(await screen.findByText("Profile updated successfully.")).toBeInTheDocument();
  });

  it("restores an authenticated customer profile route after refresh", async () => {
    sessionStorage.setItem("foodieexpress.accessToken", validAddressToken());
    window.history.replaceState({}, "", "/#/profile");
    vi.stubGlobal("fetch", vi.fn((input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(customer));
      return Promise.resolve(response(page([])));
    }));
    render(<Provider store={store}><App /></Provider>);
    expect(await screen.findByRole("heading", { name: "Your profile" })).toBeInTheDocument();
    expect(await screen.findByText("customer@example.test")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Profile" })).toBeInTheDocument();
    expect(window.location.hash).toBe("#/profile");
  });

  it("redirects unauthenticated visitors from the profile route", async () => {
    window.history.replaceState({}, "", "/#/profile");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response(page())));
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    expect(screen.queryByRole("button", { name: "Profile" })).not.toBeInTheDocument();
    expect(window.location.hash).toBe("#/restaurants");
    expect(screen.queryByRole("heading", { name: "Your profile" })).not.toBeInTheDocument();
  });

  it("authenticates through User Service and reveals only the returned user role views", async () => {
    const tokenPayload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))
      .replace(/=/g, "")
      .replace(/\+/g, "-")
      .replace(/\//g, "_");
    const accessToken = `header.${tokenPayload}.signature`;
    const fetchMock = vi.fn((input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/auth/login")) {
        return Promise.resolve(response({ accessToken, tokenType: "Bearer", expiresAt: new Date(Date.now() + 3_600_000).toISOString(), user: customer }));
      }
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(customer));
      return Promise.resolve(response({ message: "Unexpected test request" }, 404));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");

    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    const dialog = screen.getByRole("dialog", { name: "Sign in or register" });
    fireEvent.change(within(dialog).getByLabelText("Email"), { target: { value: customer.email } });
    fireEvent.change(within(dialog).getByLabelText("Password"), { target: { value: "correct-horse-battery" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(screen.getByText("Hi, Local")).toBeInTheDocument());
    expect(await screen.findByText("Welcome back, Local Customer.")).toBeInTheDocument();
    expect(screen.queryByRole("dialog", { name: "Sign in or register" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Orders" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Operations" })).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/api/auth/login"),
      expect.objectContaining({ method: "POST" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "Sign out" }));
    expect(await screen.findByText("You have been signed out.")).toBeInTheDocument();
  });

  it("shows one session-expiration toast when an authenticated protected request returns 401", async () => {
    const tokenPayload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))
      .replace(/=/g, "")
      .replace(/\+/g, "-")
      .replace(/\//g, "_");
    const accessToken = `header.${tokenPayload}.signature`;
    const fetchMock = vi.fn((input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/auth/login")) {
        return Promise.resolve(response({ accessToken, tokenType: "Bearer", expiresAt: new Date(Date.now() + 3_600_000).toISOString(), user: customer }));
      }
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(customer));
      if (url.includes("/api/orders/my-orders")) {
        return Promise.resolve(response({ message: "Token expired", code: "UNAUTHENTICATED" }, 401));
      }
      return Promise.resolve(response({ message: "Unexpected test request" }, 404));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    const dialog = screen.getByRole("dialog", { name: "Sign in or register" });
    fireEvent.change(within(dialog).getByLabelText("Email"), { target: { value: customer.email } });
    fireEvent.change(within(dialog).getByLabelText("Password"), { target: { value: "correct-horse-battery" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Sign in" }));
    await screen.findByText("Hi, Local");
    fireEvent.click(screen.getByRole("button", { name: "Orders" }));

    expect(await screen.findByText("Your session expired. Sign in again to continue.")).toBeInTheDocument();
    expect(screen.getAllByRole("status").filter((toast) =>
      toast.textContent?.includes("Your session expired. Sign in again to continue."),
    )).toHaveLength(1);
    expect(screen.queryByText("Your session is no longer valid. Sign in again to continue.")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sign in" })).toBeInTheDocument();
  });

  it("shows admin navigation only to administrators", async () => {
    const admin = { ...customer, roles: ["ADMIN"] };
    const tokenPayload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))
      .replace(/=/g, "")
      .replace(/\+/g, "-")
      .replace(/\//g, "_");
    const accessToken = `header.${tokenPayload}.signature`;
    vi.stubGlobal("fetch", vi.fn((input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/auth/login")) {
        return Promise.resolve(response({ accessToken, tokenType: "Bearer", expiresAt: new Date(Date.now() + 3_600_000).toISOString(), user: admin }));
      }
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(admin));
      if (url.includes("/api/deliveries/my-assignments?")) return Promise.resolve(response(page()));
      return Promise.resolve(response({ message: "Unexpected test request" }, 404));
    }));
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    const dialog = screen.getByRole("dialog", { name: "Sign in or register" });
    fireEvent.change(within(dialog).getByLabelText("Email"), { target: { value: admin.email } });
    fireEvent.change(within(dialog).getByLabelText("Password"), { target: { value: "correct-horse-battery" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Sign in" }));

    const deliveryTab = await screen.findByRole("button", { name: "Delivery desk" });
    fireEvent.click(deliveryTab);
    expect(await screen.findByText("Assigned delivery queue is available.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Operations" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Restaurant approvals" })).toBeInTheDocument();
  });

  it("defaults new restaurant forms and reset forms to India", async () => {
    const owner = { ...customer, roles: ["RESTAURANT_OWNER"] };
    const token = validAddressToken();
    vi.stubGlobal("fetch", vi.fn((input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/auth/login")) return Promise.resolve(response({
        accessToken: token, tokenType: "Bearer", expiresAt: new Date(Date.now() + 3_600_000).toISOString(), user: owner,
      }));
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(owner));
      if (url.includes("/api/users/me/addresses")) return Promise.resolve(response(page()));
      if (url.includes("/api/restaurants/my-restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/restaurants")) return Promise.resolve(response(restaurantRecord("672000000000000000000001")));
      return Promise.resolve(response({ message: `Unexpected test request: ${url}` }, 404));
    }));
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    await signIn(owner);
    fireEvent.click(screen.getByRole("button", { name: "Restaurant studio" }));

    const countryCode = await screen.findByLabelText("Country code") as HTMLInputElement;
    expect(countryCode).toHaveValue("IN");
    fireEvent.change(countryCode, { target: { value: "US" } });
    expect(countryCode).toHaveValue("US");

    fireEvent.change(screen.getByLabelText("Restaurant name"), { target: { value: "New Kitchen" } });
    fireEvent.change(screen.getByLabelText("Street address"), { target: { value: "12 Green Park" } });
    fireEvent.change(screen.getByLabelText("City"), { target: { value: "Ahmedabad" } });
    fireEvent.change(screen.getByLabelText("Region / state"), { target: { value: "Gujarat" } });
    fireEvent.change(screen.getByLabelText("Postal code"), { target: { value: "380001" } });
    fireEvent.click(screen.getByRole("button", { name: "Create restaurant" }));
    fireEvent.click(await screen.findByRole("button", { name: "Create another restaurant" }));
    expect(screen.getByLabelText("Country code")).toHaveValue("IN");
  });

  it("submits every new menu item in INR without exposing another currency choice", async () => {
    const owner = { ...customer, roles: ["RESTAURANT_OWNER"] };
    const restaurant = restaurantRecord("672000000000000000000001");
    const fetchMock = vi.fn((input: string | URL | Request, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/auth/login")) return Promise.resolve(response({
        accessToken: validAddressToken(),
        tokenType: "Bearer",
        expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
        user: owner,
      }));
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(owner));
      if (url.includes("/api/users/me/addresses")) return Promise.resolve(response(page()));
      if (url.includes("/api/restaurants/my-restaurants?")) return Promise.resolve(response(page([restaurant])));
      if (url.endsWith(`/api/restaurants/${restaurant.id}`)) return Promise.resolve(response(restaurant));
      if (url.includes(`/api/restaurants/${restaurant.id}/menu?`)) return Promise.resolve(response(page()));
      if (url.includes(`/api/orders/restaurant/${restaurant.id}?`)) return Promise.resolve(response(page()));
      if (url.endsWith(`/api/restaurants/${restaurant.id}/menu-items`) && init?.method === "POST") {
        return Promise.resolve(response({ ...menuItemRecord(), id: "menu-item-created" }, 201));
      }
      return Promise.resolve(response({ message: `Unexpected test request: ${url}` }, 404));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    await signIn(owner);
    fireEvent.click(screen.getByRole("button", { name: "Restaurant studio" }));
    fireEvent.click(await screen.findByRole("button", { name: "Saffron Kitchen" }));

    expect(await screen.findByLabelText("Price · INR (₹)")).toBeInTheDocument();
    expect(screen.queryByLabelText(/Currency/)).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Item name"), { target: { value: "Paneer Bowl" } });
    fireEvent.change(screen.getByLabelText("Price · INR (₹)"), { target: { value: "250.00" } });
    fireEvent.click(screen.getByRole("button", { name: "Add to menu" }));

    await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) =>
      String(url).endsWith(`/api/restaurants/${restaurant.id}/menu-items`)
      && init?.method === "POST"
      && JSON.parse(String(init.body)).currency === "INR",
    )).toBe(true));
  });

  it("keeps legacy prices out of carts until an explicit INR repricing succeeds", async () => {
    const owner = { ...customer, roles: ["RESTAURANT_OWNER"] };
    const buyer = { ...customer, id: "user-buyer", email: "buyer@example.test" };
    const restaurant = restaurantRecord("672000000000000000000001");
    const legacyItem = { ...menuItemRecord(), id: "menu-item-legacy", price: "10.00", currency: "USD" };
    let persistedItem = legacyItem;
    let updateAttempts = 0;
    let authenticatedUser: typeof customer = owner;
    const fetchMock = vi.fn((input: string | URL | Request, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page([restaurant])));
      if (url.endsWith("/api/auth/login")) {
        const credentials = JSON.parse(String(init?.body));
        authenticatedUser = credentials.email === buyer.email ? buyer : owner;
        return Promise.resolve(response({
          accessToken: validAddressToken(),
          tokenType: "Bearer",
          expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
          user: authenticatedUser,
        }));
      }
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(authenticatedUser));
      if (url.includes("/api/users/me/addresses")) return Promise.resolve(response(page()));
      if (url.includes("/api/restaurants/my-restaurants?")) return Promise.resolve(response(page([restaurant])));
      if (url.endsWith(`/api/restaurants/${restaurant.id}`)) return Promise.resolve(response(restaurant));
      if (url.includes(`/api/restaurants/${restaurant.id}/menu?`)) return Promise.resolve(response(page([persistedItem])));
      if (url.includes(`/api/orders/restaurant/${restaurant.id}?`)) return Promise.resolve(response(page()));
      if (url.endsWith(`/api/menu-items/${legacyItem.id}`) && init?.method === "PUT") {
        updateAttempts += 1;
        const body = JSON.parse(String(init.body));
        expect(body.currency).toBe("INR");
        if (updateAttempts === 1) {
          return Promise.resolve(response({
            message: "Request validation failed",
            fieldErrors: [{ field: "currency", message: 'must match "^INR$"' }],
          }, 400));
        }
        persistedItem = { ...persistedItem, ...body, price: "250.00", currency: "INR" };
        return Promise.resolve(response(persistedItem));
      }
      return Promise.resolve(response({ message: `Unexpected test request: ${url}` }, 404));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);
    await screen.findByRole("button", { name: /Saffron Kitchen/ });
    await signIn(owner);
    fireEvent.click(screen.getByRole("button", { name: "Restaurant studio" }));
    fireEvent.click(await screen.findByRole("button", { name: "Saffron Kitchen" }));
    expect(await screen.findByText("Legacy USD $10.00 · Available")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    expect(await screen.findByText(/legacy USD menu item/)).toBeInTheDocument();
    const price = screen.getByLabelText("Price · INR (₹)");
    expect(price).toHaveValue("");
    fireEvent.change(price, { target: { value: "250.00" } });
    fireEvent.click(screen.getByRole("button", { name: "Save menu changes" }));

    expect(await screen.findByText(/currency: must match "\^INR\$"/)).toBeInTheDocument();
    expect(screen.getByLabelText("Price · INR (₹)")).toHaveValue("250.00");
    expect(screen.getByText(/legacy USD menu item/i)).toBeInTheDocument();
    expect(screen.getByText("Legacy USD $10.00 · Available")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Save menu changes" }));
    await waitFor(() => expect(screen.getByText("₹250.00 · Available")).toBeInTheDocument());
    expect(updateAttempts).toBe(2);

    fireEvent.click(screen.getByRole("button", { name: "Discover" }));
    await screen.findByRole("heading", { name: "Find your kind of flavor" });
    fireEvent.click(await screen.findByRole("button", { name: /Saffron Kitchen/ }));
    expect(await screen.findByText("₹250.00")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add +" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Close menu" }));
    fireEvent.click(screen.getByRole("button", { name: "Sign out" }));
    await signIn(buyer);
    fireEvent.click(await screen.findByRole("button", { name: /Saffron Kitchen/ }));
    expect(await screen.findByText("₹250.00")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Add +" }));
    fireEvent.click(screen.getByRole("button", { name: /Cart, 1 items/ }));
    expect(await screen.findByText("₹250.00 each")).toBeInTheDocument();
    expect(screen.getAllByText("₹250.00")).toHaveLength(2);
  });

  it("loads pending approvals and refreshes after a successful approval", async () => {
    const admin = { ...customer, roles: ["ADMIN"] };
    const pending = { ...restaurantRecord("restaurant-pending"), ownerUserId: "owner-123" };
    const fetchMock = vi.fn((input: string | URL | Request, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/auth/login")) return Promise.resolve(response({
        accessToken: validAddressToken(), tokenType: "Bearer", expiresAt: new Date(Date.now() + 3_600_000).toISOString(), user: admin,
      }));
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(admin));
      if (url.includes("/api/users/me/addresses")) return Promise.resolve(response(page()));
      if (url.includes("/api/admin/restaurants?")) return Promise.resolve(response(page(
        fetchMock.mock.calls.some(([calledUrl, options]) =>
          String(calledUrl).includes("/api/admin/restaurants/restaurant-pending/approval")
          && (options as RequestInit | undefined)?.method === "PATCH",
        ) ? [] : [{ ...pending, status: "PENDING_APPROVAL" }],
      )));
      if (url.endsWith("/api/admin/restaurants/restaurant-pending/approval")) {
        expect(init?.method).toBe("PATCH");
        expect(JSON.parse(String(init?.body))).toEqual({ status: "ACTIVE" });
        return Promise.resolve(response({ ...pending, status: "ACTIVE" }));
      }
      return Promise.resolve(response({ message: "Unexpected test request" }, 404));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    await signIn(admin);
    fireEvent.click(screen.getByRole("button", { name: "Restaurant approvals" }));

    expect(await screen.findByText("Saffron Kitchen")).toBeInTheDocument();
    expect(screen.getByText("owner-123")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Approve" }));
    expect(await screen.findByText("Restaurant approved.")).toBeInTheDocument();
    expect(await screen.findByText("No restaurants awaiting approval")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8082/api/admin/restaurants?status=PENDING_APPROVAL&page=0&size=20&sort=name%2Casc",
      expect.anything(),
    );
  });

  it("keeps an approval visible and reports backend failures", async () => {
    const admin = { ...customer, roles: ["ADMIN"] };
    vi.stubGlobal("fetch", vi.fn((input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/auth/login")) return Promise.resolve(response({
        accessToken: validAddressToken(), tokenType: "Bearer", expiresAt: new Date(Date.now() + 3_600_000).toISOString(), user: admin,
      }));
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(admin));
      if (url.includes("/api/users/me/addresses")) return Promise.resolve(response(page()));
      if (url.includes("/api/admin/restaurants?")) return Promise.resolve(response(page([
        { ...restaurantRecord("restaurant-pending"), ownerUserId: "owner-123", status: "PENDING_APPROVAL" },
      ])));
      if (url.endsWith("/approval")) return Promise.resolve(response({ message: "Approval unavailable" }, 409));
      return Promise.resolve(response({ message: "Unexpected test request" }, 404));
    }));
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    await signIn(admin);
    fireEvent.click(screen.getByRole("button", { name: "Restaurant approvals" }));
    await screen.findByText("Saffron Kitchen");
    fireEvent.click(screen.getByRole("button", { name: "Reject" }));

    expect(await screen.findByText("Approval unavailable")).toBeInTheDocument();
    expect(screen.getByText("Saffron Kitchen")).toBeInTheDocument();
  });

  it("lists admin platform orders and loads the returned delivery status", async () => {
    const admin = { ...customer, roles: ["ADMIN"] };
    const order = {
      id: "order-1", customerId: "customer-1", restaurantId: "restaurant-1", items: [
        { menuItemId: "item-1", name: "Paneer Bowl", quantity: 2, unitPrice: "12.50", lineTotal: "25.00" },
      ],
      deliveryAddress: {}, currency: "INR", subtotal: "25.00", deliveryFee: "0.00", discount: "0.00", total: "25.00",
      paymentStatus: "SIMULATED_UNPAID", status: "CONFIRMED", deliveryProvisioningStatus: "COMPLETE",
      deliveryId: "delivery-1", cancellationStatus: "NONE", cancellationReason: null, rejectionReason: null,
      createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), statusUpdatedAt: new Date().toISOString(),
    };
    const fetchMock = vi.fn((input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/auth/login")) return Promise.resolve(response({
        accessToken: validAddressToken(), tokenType: "Bearer", expiresAt: new Date(Date.now() + 3_600_000).toISOString(), user: admin,
      }));
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(admin));
      if (url.includes("/api/users/me/addresses")) return Promise.resolve(response(page()));
      if (url.includes("/api/admin/orders?")) return Promise.resolve(response(page([order])));
      if (url.endsWith("/api/deliveries/delivery-1")) return Promise.resolve(response({
        id: "delivery-1", orderId: "order-1", restaurantId: "restaurant-1", customerId: "customer-1",
        pickupAddress: {}, deliveryAddress: {}, partner: null, currency: "INR", status: "PICKED_UP",
        cancellationReason: null, events: [], orderCallbackPending: false,
        createdAt: order.createdAt, updatedAt: order.updatedAt,
      }));
      return Promise.resolve(response({ message: `Unexpected test request: ${url}` }, 404));
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    await signIn(admin);
    fireEvent.click(screen.getByRole("button", { name: "Operations" }));
    expect(await screen.findByText("Order order-1")).toBeInTheDocument();
    expect(screen.getByText("Customer customer-1 · Restaurant restaurant-1")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Details" }));
    expect(await screen.findByText(/Delivery status: Picked Up/)).toBeInTheDocument();
  });

  it("does not expose admin navigation to customers", async () => {
    vi.stubGlobal("fetch", vi.fn((input: string | URL | Request) => {
      const url = String(input);
      if (url.includes("/api/restaurants?")) return Promise.resolve(response(page()));
      if (url.endsWith("/api/auth/login")) return Promise.resolve(response({
        accessToken: validAddressToken(), tokenType: "Bearer", expiresAt: new Date(Date.now() + 3_600_000).toISOString(), user: customer,
      }));
      if (url.endsWith("/api/users/me")) return Promise.resolve(response(customer));
      return Promise.resolve(response({ message: "Unexpected test request" }, 404));
    }));
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");
    await signIn(customer);
    expect(screen.queryByRole("button", { name: "Restaurant approvals" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Operations" })).not.toBeInTheDocument();
    window.history.replaceState({}, "", "/#/approvals");
    window.dispatchEvent(new HashChangeEvent("hashchange"));
    await waitFor(() => expect(window.location.hash).toBe("#/restaurants"));
  });
});
