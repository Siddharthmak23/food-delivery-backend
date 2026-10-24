import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError, request, tokenExpiry } from "./api";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("service API client", () => {
  it("sends the user token and checkout idempotency key only to the API request", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{"id":"order-1"}', { status: 201 }));
    vi.stubGlobal("fetch", fetchMock);

    await request("http://localhost:8083", "/api/orders", {
      method: "POST",
      token: "user-access-token",
      headers: { "Idempotency-Key": "checkout-attempt-1" },
      body: { restaurantId: "restaurant-1", items: [], addressId: "address-1" },
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8083/api/orders",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          Authorization: "Bearer user-access-token",
          "Idempotency-Key": "checkout-attempt-1",
          "Content-Type": "application/json",
        }),
        body: JSON.stringify({ restaurantId: "restaurant-1", items: [], addressId: "address-1" }),
      }),
    );
  });

  it("surfaces API validation details instead of treating errors as successful data", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      JSON.stringify({
        status: 400,
        code: "VALIDATION_FAILED",
        message: "Request validation failed",
        fieldErrors: [{ field: "quantity", code: "Min", message: "must be at least one" }],
        traceId: "trace-123",
      }),
      { status: 400, headers: { "Content-Type": "application/json" } },
    )));

    await expect(request("http://localhost:8083", "/api/orders")).rejects.toMatchObject({
      name: "ApiRequestError",
      status: 400,
      code: "VALIDATION_FAILED",
      traceId: "trace-123",
      fieldErrors: [{ field: "quantity", code: "Min", message: "must be at least one" }],
    } satisfies Partial<ApiRequestError>);
  });

  it("clears an invalid session through the unauthorized callback", async () => {
    const onUnauthorized = vi.fn();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ status: 401, code: "UNAUTHENTICATED", message: "Authentication is required" }),
      { status: 401, headers: { "Content-Type": "application/json" } },
    )));

    await expect(request("http://localhost:8081", "/api/users/me", {
      token: "existing-user-token",
      onUnauthorized,
    })).rejects.toMatchObject({ status: 401, unauthorizedHandled: true });
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it("does not treat authentication endpoint 401 responses as expired sessions", async () => {
    const onUnauthorized = vi.fn();
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ status: 401, code: "INVALID_CREDENTIALS", message: "Email or password is incorrect" }),
      { status: 401, headers: { "Content-Type": "application/json" } },
    )));

    await expect(request("http://localhost:8081", "/api/auth/login", {
      method: "POST",
      token: "existing-user-token",
      onUnauthorized,
    })).rejects.toMatchObject({ status: 401, message: "Email or password is incorrect", unauthorizedHandled: false });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it("reads expiry from a JWT payload without trusting it as authentication", () => {
    const payload = btoa(JSON.stringify({ exp: 2_000_000_000 }))
      .replace(/=/g, "")
      .replace(/\+/g, "-")
      .replace(/\//g, "_");
    expect(tokenExpiry(`header.${payload}.signature`)).toBe(2_000_000_000_000);
    expect(tokenExpiry("not-a-token")).toBeNull();
  });
});
