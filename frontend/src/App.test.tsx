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

  function fillRegistration(dialog: HTMLElement, password = "correct-horse-battery") {
    fireEvent.change(within(dialog).getByLabelText("Your name"), { target: { value: "Local Customer" } });
    fireEvent.change(within(dialog).getByLabelText("Email"), { target: { value: "new@example.test" } });
    fireEvent.change(within(dialog).getByLabelText("Password"), { target: { value: password } });
  }

  it("opens registration from sign-in and explains customer-only public signup", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response(page())));
    render(<Provider store={store}><App /></Provider>);
    await screen.findByText("No kitchens found");

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
    expect(JSON.parse(String(updateCall?.[1]?.body))).toMatchObject({ phone: "+919876543210" });

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

  it("allows administrators to use only the documented delivery operations view", async () => {
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
  });
});
