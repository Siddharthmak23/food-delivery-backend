import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useDispatch, useSelector } from "react-redux";
import { addItem, clearCart, setQuantity, type AppDispatch, type CartLine, type RootState } from "./store";
import { ApiRequestError, createApi, tokenExpiry } from "./api";
import type { Address, AddressInput, Delivery, MenuItem, Order, Restaurant, User } from "./types";

type View = "restaurants" | "addresses" | "orders" | "owner" | "delivery" | "admin";
const viewTitles: Record<View, string> = {
  restaurants: "Discover",
  addresses: "My addresses",
  orders: "My orders",
  owner: "Restaurant studio",
  delivery: "Delivery desk",
  admin: "Operations",
};
const rolesForView: Partial<Record<View, string>> = {
  addresses: "CUSTOMER",
  orders: "CUSTOMER",
  owner: "RESTAURANT_OWNER",
  admin: "ADMIN",
};
const initialAddress: AddressInput = {
  label: "",
  recipientName: "",
  line1: "",
  line2: "",
  city: "",
  region: "",
  postalCode: "",
  countryCode: "IN",
  phone: "",
};
const indianRegions = [
  "Andaman and Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar",
  "Chandigarh", "Chhattisgarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Goa",
  "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand", "Karnataka",
  "Kerala", "Ladakh", "Lakshadweep", "Madhya Pradesh", "Maharashtra", "Manipur",
  "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Puducherry", "Punjab", "Rajasthan",
  "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand",
  "West Bengal",
];

function canAccessView(view: View, user: User | null) {
  if (view === "restaurants") return true;
  if (!user) return false;
  if (view === "delivery") return user.roles.includes("DELIVERY_OPERATOR") || user.roles.includes("ADMIN");
  const requiredRole = rolesForView[view];
  return requiredRole !== undefined && user.roles.includes(requiredRole);
}

function formatMoney(value: string) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(Number(value));
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

type Api = ReturnType<typeof createApi>;
type Run = <T,>(action: () => Promise<T>) => Promise<T | undefined>;
type ToastKind = "success" | "error" | "warning" | "info";

type ToastMessage = {
  id: number;
  message: string;
  kind: ToastKind;
};

function DiscoveryView(props: {
  api: Api;
  user: User | null;
  cart: CartLine[];
  busy: boolean;
  onRun: Run;
  onAdd: (item: MenuItem) => void;
  onQuantity: (id: string, quantity: number) => void;
  onClear: () => void;
  onCheckout: (addressId: string) => Promise<void>;
}) {
  const [restaurants, setRestaurants] = useState<Restaurant[]>([]);
  const [query, setQuery] = useState("");
  const [cuisine, setCuisine] = useState("");
  const [search, setSearch] = useState({ q: "", cuisine: "" });
  const [selected, setSelected] = useState<Restaurant | null>(null);
  const [menu, setMenu] = useState<MenuItem[]>([]);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [addressId, setAddressId] = useState("");
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const subtotal = props.cart.reduce((sum, line) => sum + Number(line.price) * line.quantity, 0);

  useEffect(() => {
    setLoading(true);
    props.api.listRestaurants({ q: search.q || undefined, cuisine: search.cuisine || undefined, page })
      .then((result) => {
        setRestaurants(result.items);
        setTotalPages(result.totalPages);
      })
      .catch((reason: unknown) => props.onRun(() => Promise.reject(reason)))
      .finally(() => setLoading(false));
  }, [props.api, props.onRun, page, search]);

  useEffect(() => {
    if (!selected) {
      setMenu([]);
      return;
    }
    setLoading(true);
    Promise.all([props.api.restaurant(selected.id), props.api.menu(selected.id)])
      .then(([restaurant, page]) => {
        setSelected(restaurant);
        setMenu(page.items);
      })
      .catch((reason: unknown) => props.onRun(() => Promise.reject(reason)))
      .finally(() => setLoading(false));
  }, [props.api, props.onRun, selected?.id]);

  useEffect(() => {
    if (!props.user) {
      setAddresses([]);
      setAddressId("");
      return;
    }
    props.api.listAddresses()
      .then((page) => {
        const eligibleAddresses = page.items.filter((address) => address.countryCode === "IN");
        setAddresses(page.items);
        setAddressId((existing) => eligibleAddresses.some((address) => address.id === existing)
          ? existing
          : eligibleAddresses[0]?.id || "");
      })
      .catch((reason: unknown) => props.onRun(() => Promise.reject(reason)));
  }, [props.api, props.onRun, props.user?.id]);

  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <div className="eyebrow"><span className="eyebrow-dot" /> GOOD FOOD, RIGHT AROUND THE CORNER</div>
          <h1>Your next<br /><em>favorite meal</em><br />is closer than you think.</h1>
          <p>Local kitchens, fresh menus, and a little something delicious for whatever kind of day you’re having.</p>
          <a className="button button-primary" href="#restaurants-list">Explore restaurants <span aria-hidden="true">↓</span></a>
        </div>
        <div className="hero-art" aria-label="Illustration of a meal bowl">
          <div className="hero-sun" />
          <div className="hero-orbit orbit-one" />
          <div className="hero-orbit orbit-two" />
          <div className="food-bowl"><span className="bowl-leaf leaf-a" /><span className="bowl-leaf leaf-b" /><span className="bowl-tomato" /><span className="bowl-pasta" /><span className="bowl-bean" /></div>
          <span className="floating-note note-one">Made fresh daily <b>✳</b></span>
          <span className="floating-note note-two">Your neighborhood, served.</span>
        </div>
      </section>

      {props.user && props.cart.length > 0 && (
        <section className="cart-panel">
          <div className="section-heading compact-heading">
            <div><span className="eyebrow">YOUR ORDER</span><h2>Something delicious</h2></div>
            <button className="text-button" onClick={props.onClear}>Clear cart</button>
          </div>
          <div className="cart-lines">
            {props.cart.map((line) => (
              <div className="cart-line" key={line.menuItemId}>
                <div><strong>{line.name}</strong><small>{formatMoney(line.price)} each</small></div>
                <div className="quantity-control">
                  <button aria-label={`Remove one ${line.name}`} onClick={() => props.onQuantity(line.menuItemId, line.quantity - 1)}>−</button>
                  <span>{line.quantity}</span>
                  <button aria-label={`Add one ${line.name}`} disabled={line.quantity >= 100} onClick={() => props.onQuantity(line.menuItemId, line.quantity + 1)}>+</button>
                </div>
                <strong>{formatMoney((Number(line.price) * line.quantity).toFixed(2))}</strong>
              </div>
            ))}
          </div>
          <div className="checkout-row">
            <label className="field checkout-address">
              <span>Deliver to a saved address</span>
              <select value={addressId} onChange={(event) => setAddressId(event.target.value)} required>
                <option value="">Choose address</option>
                {addresses.filter((address) => address.countryCode === "IN").map((address) => <option value={address.id} key={address.id}>{address.label} · {address.line1}, {address.city}</option>)}
              </select>
              {!addresses.some((address) => address.countryCode === "IN") && <small>Add an Indian delivery address before checkout.</small>}
            </label>
            <div className="checkout-total"><small>Estimated subtotal · backend recalculates</small><strong>{formatMoney(subtotal.toFixed(2))}</strong><span>+ $3.99 delivery fee at checkout</span></div>
            <button
              className="button button-primary"
              disabled={props.busy || !addressId || !addresses.length}
              onClick={() => void props.onCheckout(addressId)}
            >{props.busy ? "Placing order…" : "Place order"}</button>
          </div>
          <p className="fine-print">Prices are estimates until checkout. Payment is simulated and no charge is made.</p>
        </section>
      )}

      <section className="restaurants-section" id="restaurants-list">
        <div className="section-heading">
          <div><span className="eyebrow">A GOOD PLACE TO START</span><h2>Find your kind of flavor</h2></div>
          <span className="result-count">{restaurants.length} kitchens</span>
        </div>
        <form className="search-bar" onSubmit={(event) => { event.preventDefault(); setPage(0); setSearch({ q: query.trim(), cuisine: cuisine.trim() }); }}>
          <label className="search-input"><span aria-hidden="true">⌕</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Restaurant or dish" aria-label="Search restaurants" /></label>
          <label className="search-input cuisine-input"><span aria-hidden="true">◌</span><input value={cuisine} onChange={(event) => setCuisine(event.target.value)} placeholder="Cuisine" aria-label="Filter by cuisine" /></label>
          <button className="button button-dark" type="submit">Search</button>
        </form>
        {loading && !selected ? <Loading label="Finding neighborhood favorites" /> : restaurants.length === 0 ? (
          <EmptyState title="No kitchens found" text="Try a different restaurant name or cuisine." />
        ) : (
          <div className="restaurant-grid">
            {restaurants.map((restaurant, index) => (
              <button className="restaurant-card" key={restaurant.id} onClick={() => setSelected(restaurant)}>
                <span className={`restaurant-art art-${index % 4}`}><span>{restaurant.name.slice(0, 1).toUpperCase()}</span><i>✳</i></span>
                <span className="restaurant-card-body">
                  <span className="card-title-row"><strong>{restaurant.name}</strong><span className="rating">✦ <b>Neighborhood favorite</b></span></span>
                  <span className="restaurant-meta">{restaurant.cuisines.join(" · ") || "Local kitchen"} <span>·</span> {restaurant.address.city}</span>
                  <span className="restaurant-desc">{restaurant.description || "Thoughtful cooking, made fresh for your table."}</span>
                </span>
                <span className="card-arrow" aria-hidden="true">↗</span>
              </button>
            ))}
          </div>
        )}
        {totalPages > 1 && <div className="pagination"><button className="button button-outline button-small" disabled={page === 0 || loading} onClick={() => setPage((current) => current - 1)}>Previous</button><span>Page {page + 1} of {totalPages}</span><button className="button button-outline button-small" disabled={page + 1 >= totalPages || loading} onClick={() => setPage((current) => current + 1)}>Next</button></div>}
      </section>

      {selected && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setSelected(null); }}>
          <section className="restaurant-modal" role="dialog" aria-modal="true" aria-labelledby="restaurant-dialog-title">
            <button className="modal-close" onClick={() => setSelected(null)} aria-label="Close menu">×</button>
            <div className="modal-banner">
              <div className="eyebrow">TODAY’S MENU</div>
              <h2 id="restaurant-dialog-title">{selected.name}</h2>
              <p>{selected.cuisines.join(" · ")} · {selected.address.city}</p>
            </div>
            <div className="menu-list">
              {loading ? <Loading label="Loading menu" /> : menu.length === 0 ? <EmptyState title="Menu is empty" text="This kitchen hasn’t published any menu items yet." /> : menu.map((item) => (
                <article className="menu-item" key={item.id}>
                  <span className="menu-item-icon">✳</span>
                  <div className="menu-item-copy"><strong>{item.name}</strong><small>{item.description || item.category}</small><span>{formatMoney(item.price)}</span></div>
                  {item.available
                    ? <button className="button button-outline button-small" onClick={() => props.onAdd(item)}>Add +</button>
                    : <span className="unavailable-tag">Sold out</span>}
                </article>
              ))}
            </div>
          </section>
        </div>
      )}
    </>
  );
}

function AddressesView({ api, busy, onRun, onNotify }: {
  api: Api;
  busy: boolean;
  onRun: Run;
  onNotify: (message: string, kind: ToastKind) => void;
}) {
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [editing, setEditing] = useState<string | null>(null);
  const [form, setForm] = useState<AddressInput>(initialAddress);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [validationErrors, setValidationErrors] = useState<Partial<Record<keyof AddressInput, string>>>({});
  const [touchedFields, setTouchedFields] = useState<Set<keyof AddressInput>>(new Set());
  const [submitted, setSubmitted] = useState(false);
  const saveInProgress = useRef(false);
  const editingRef = useRef(editing);
  editingRef.current = editing;
  const refresh = useCallback(async () => {
    const result = await onRun(() => api.listAddresses());
    if (result) {
      setAddresses(result.items);
      const activeEdit = editingRef.current;
      if (activeEdit && !result.items.some((address) => address.id === activeEdit)) {
        setEditing(null);
        setForm(initialAddress);
        setValidationErrors({});
        setTouchedFields(new Set());
        setSubmitted(false);
        setDialogOpen(false);
      }
    }
    return result;
  }, [api, onRun]);
  useEffect(() => { void refresh(); }, [refresh]);

  const closeDialog = () => {
    setEditing(null);
    setForm(initialAddress);
    setValidationErrors({});
    setTouchedFields(new Set());
    setSubmitted(false);
    setDialogOpen(false);
  };
  const updateForm = (next: AddressInput, field: keyof AddressInput) => {
    setForm(next);
    if (submitted || touchedFields.has(field)) setValidationErrors(validateAddress(next));
  };
  const blurField = (field: keyof AddressInput) => {
    setTouchedFields((current) => new Set(current).add(field));
    setValidationErrors(validateAddress(form));
  };
  const openNewAddress = () => {
    setEditing(null);
    setForm(initialAddress);
    setValidationErrors({});
    setTouchedFields(new Set());
    setSubmitted(false);
    setDialogOpen(true);
  };
  const openEditAddress = (address: Address) => {
    setEditing(address.id);
    setForm({ ...address, line2: address.line2 ?? "" });
    setValidationErrors({});
    setTouchedFields(new Set());
    setSubmitted(false);
    setDialogOpen(true);
  };
  const deleteAddress = async (addressId: string) => {
    const deleted = await onRun(async () => {
      await api.deleteAddress(addressId);
      return true;
    });
    if (!deleted) return;
    if (editing === addressId) closeDialog();
    setAddresses((current) => current.filter((address) => address.id !== addressId));
    if (await refresh()) onNotify("Address deleted successfully.", "success");
  };
  const save = async (event: FormEvent) => {
    event.preventDefault();
    if (busy || saveInProgress.current) return;
    setSubmitted(true);
    const errors = validateAddress(form);
    setValidationErrors(errors);
    if (Object.keys(errors).length > 0) return;
    const normalizedPhone = normalizeIndianPhone(form.phone);
    if (!normalizedPhone) return;
    saveInProgress.current = true;
    const normalizedForm = { ...form, countryCode: "IN", phone: normalizedPhone };
    const wasEditing = editing !== null;
    try {
      const result = await onRun(() => editing
        ? api.updateAddress(editing, normalizedForm)
        : api.createAddress(normalizedForm));
      if (result) {
        closeDialog();
        if (await refresh()) onNotify(wasEditing ? "Address updated successfully." : "Address saved successfully.", "success");
      }
    } finally {
      saveInProgress.current = false;
    }
  };

  return (
    <section className="workspace">
      <PageIntro eyebrow="THE DETAILS THAT MAKE IT YOURS" title="Saved addresses" text="Keep a delivery spot ready for the next time something sounds good." />
      <section className="surface-card address-management">
        <div className="section-heading compact-heading">
          <div><span className="eyebrow">YOUR PLACES</span><h2>{addresses.length} saved</h2></div>
          <button className="button button-primary button-small" disabled={busy} onClick={openNewAddress}>Add Address</button>
        </div>
        {addresses.length === 0 ? <EmptyState title="No addresses yet" text="Add a delivery address to place your first order." /> : (
          <div className="address-list">
            {addresses.map((address) => (
              <article className="address-card" key={address.id}>
                <span className="address-pin">{address.label.toLowerCase().includes("work") ? "⌂" : "⌖"}</span>
                <div><strong>{address.label}</strong><p>{address.recipientName}<br />{address.line1}{address.line2 ? `, ${address.line2}` : ""}<br />{address.city}, {address.region} {address.postalCode}<br />{address.countryCode}</p><small>{address.phone}</small></div>
                <div className="address-actions">
                  <button className="text-button" disabled={busy} onClick={() => openEditAddress(address)}>Edit</button>
                  <button className="text-button danger-text" disabled={busy} onClick={() => void deleteAddress(address.id)}>Delete</button>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
      {dialogOpen && <div className="modal-backdrop address-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) closeDialog(); }}>
        <form className="surface-card address-dialog" role="dialog" aria-modal="true" aria-labelledby="address-dialog-title" onSubmit={(event) => void save(event)} noValidate>
          <div className="section-heading compact-heading"><div><span className="eyebrow">{editing ? "UPDATE A PLACE" : "ADD A NEW PLACE"}</span><h2 id="address-dialog-title">{editing ? "Edit address" : "Add Address"}</h2></div></div>
          {editing && addresses.find((address) => address.id === editing)?.countryCode !== "IN" && <p className="form-hint">This saved address is outside India. It will be updated to India only when you save your changes.</p>}
          <AddressFields
            value={form}
            onChange={updateForm}
            onBlur={blurField}
            errors={Object.fromEntries(
              Object.entries(validationErrors).filter(([field]) => submitted || touchedFields.has(field as keyof AddressInput)),
            )}
          />
          <div className="address-dialog-actions">
            {editing && <button className="button button-outline danger-button address-dialog-delete" type="button" disabled={busy} onClick={() => void deleteAddress(editing)}>Delete Address</button>}
            <button className="button button-outline" type="button" disabled={busy} onClick={closeDialog}>Cancel</button>
            <button className="button button-primary" disabled={busy}>{busy ? "Saving…" : "Save Address"}</button>
          </div>
        </form>
      </div>}
    </section>
  );
}

function OrdersView({ api, busy, onRun }: { api: Api; busy: boolean; onRun: Run }) {
  const [orders, setOrders] = useState<Order[]>([]);
  const [selected, setSelected] = useState<Order | null>(null);
  const [delivery, setDelivery] = useState<Delivery | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const refresh = useCallback(async () => {
    const result = await onRun(() => api.myOrders(page));
    if (result) {
      setOrders(result.items);
      setTotalPages(result.totalPages);
    }
    setLoading(false);
  }, [api, onRun, page]);
  useEffect(() => { void refresh(); }, [refresh]);
  const openOrder = async (order: Order) => {
    setSelected(order);
    setDelivery(null);
    if (order.deliveryId) {
      const result = await onRun(() => api.delivery(order.deliveryId!));
      if (result) setDelivery(result);
    } else if (order.deliveryProvisioningStatus === "COMPLETE") {
      const result = await onRun(() => api.deliveryByOrder(order.id));
      if (result) setDelivery(result);
    }
  };
  const cancel = async (id: string) => {
    const result = await onRun(() => api.cancelOrder(id));
    if (result) {
      setSelected(result);
      await refresh();
    }
  };
  const track = async (id: string) => {
    const timeline = await onRun(() => api.deliveryTimeline(id));
    if (timeline && delivery) setDelivery({ ...delivery, events: timeline.events });
  };

  return (
    <section className="workspace">
      <PageIntro eyebrow="EVERY GOOD BITE, ACCOUNTED FOR" title="Your orders" text="Follow each order from kitchen confirmation through the delivery handoff." />
      <div className="orders-layout">
        <div className="order-list surface-card">
          {loading ? <Loading label="Loading your orders" /> : orders.length === 0 ? <EmptyState title="Your next favorite is waiting" text="Orders you place will show up here." /> : orders.map((order) => (
            <button className={`order-list-item ${selected?.id === order.id ? "selected" : ""}`} key={order.id} onClick={() => void openOrder(order)}>
              <span className="order-initial">✳</span>
              <span className="order-list-copy"><strong>Order {order.id.slice(-6).toUpperCase()}</strong><small>{formatDate(order.createdAt)} · {order.items.length} items</small></span>
              <span className="status-pill">{prettyStatus(order.status)}</span>
              <strong>{formatMoney(order.total)}</strong>
            </button>
          ))}
          {totalPages > 1 && <div className="pagination"><button className="button button-outline button-small" disabled={page === 0 || loading} onClick={() => setPage((current) => current - 1)}>Previous</button><span>{page + 1} / {totalPages}</span><button className="button button-outline button-small" disabled={page + 1 >= totalPages || loading} onClick={() => setPage((current) => current + 1)}>Next</button></div>}
        </div>
        <div className="surface-card order-detail">
          {!selected ? <EmptyState title="Select an order" text="Order details and delivery updates will appear here." /> : (
            <>
              <div className="detail-heading"><div><span className="eyebrow">ORDER DETAILS</span><h2>{prettyStatus(selected.status)}</h2><small>Placed {formatDate(selected.createdAt)}</small></div><span className="status-pill">{prettyStatus(selected.paymentStatus)}</span></div>
              <div className="progress-track">
                {["PLACED", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "OUT_FOR_DELIVERY", "DELIVERED"].map((status, index) => {
                  const active = status === selected.status || (selected.status === "OUT_FOR_DELIVERY" && index < 5) || (selected.status === "DELIVERED" && index < 6) || (selected.status === "PREPARING" && index < 3) || (selected.status === "READY_FOR_PICKUP" && index < 4);
                  return <span className={active ? "progress-step complete" : "progress-step"} key={status}><i>{active ? "✓" : index + 1}</i><small>{prettyStatus(status)}</small></span>;
                })}
              </div>
              {selected.items.map((item) => <div className="detail-line" key={item.menuItemId}><span>{item.quantity} × {item.name}</span><strong>{formatMoney(item.lineTotal)}</strong></div>)}
              <div className="order-total-lines"><span>Subtotal <b>{formatMoney(selected.subtotal)}</b></span><span>Delivery fee <b>{formatMoney(selected.deliveryFee)}</b></span><span className="total-line">Total <b>{formatMoney(selected.total)}</b></span></div>
              <div className="delivery-address-block"><span className="eyebrow">DELIVERY ADDRESS</span><p>{selected.deliveryAddress.recipientName}<br />{selected.deliveryAddress.line1}, {selected.deliveryAddress.city}, {selected.deliveryAddress.region} {selected.deliveryAddress.postalCode}</p></div>
              {selected.status === "CANCELLED" && <p className="inline-note">This order was cancelled{selected.cancellationReason ? `: ${selected.cancellationReason}` : ""}.</p>}
              {selected.deliveryProvisioningStatus !== "NOT_REQUESTED" && (
                <section className="tracking-card">
                  <div className="tracking-heading"><div><span className="eyebrow">DELIVERY TRACKING</span><h3>{delivery ? prettyStatus(delivery.status) : "Delivery setup in progress"}</h3></div>
                    {delivery && <button className="text-button" onClick={() => void track(delivery.id)}>Refresh timeline</button>}
                  </div>
                  {delivery ? <>
                    <p className="small-copy">{delivery.partner?.displayName ?? "A delivery partner will be assigned"}{delivery.orderCallbackPending ? " · order status sync pending" : ""}</p>
                    <ol className="timeline">{delivery.events.map((event) => <li key={event.id}><i /><div><strong>{prettyStatus(event.type)}</strong><small>{formatDate(event.occurredAt)}{event.callbackPending ? " · sync retry pending" : ""}</small></div></li>)}</ol>
                  </> : <p className="small-copy">Provisioning status: {prettyStatus(selected.deliveryProvisioningStatus)}. No delivery record is available yet.</p>}
                </section>
              )}
              {selected.status === "PLACED" && <button className="button button-outline danger-button" disabled={busy} onClick={() => void cancel(selected.id)}>Cancel order</button>}
            </>
          )}
        </div>
      </div>
    </section>
  );
}

function OwnerView({ api, busy, onRun }: { api: Api; busy: boolean; onRun: Run }) {
  const [restaurantId, setRestaurantId] = useState("");
  const [ownedRestaurants, setOwnedRestaurants] = useState<Restaurant[]>([]);
  const [restaurant, setRestaurant] = useState<Restaurant | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [orderPage, setOrderPage] = useState(0);
  const [orderPages, setOrderPages] = useState(0);
  const [menu, setMenu] = useState<MenuItem[]>([]);
  const [editingMenuId, setEditingMenuId] = useState<string | null>(null);
  const [restaurantForm, setRestaurantForm] = useState({
    name: "", description: "", cuisines: "Comfort food",
    line1: "", line2: "", city: "", region: "", postalCode: "", countryCode: "US",
  });
  const [itemForm, setItemForm] = useState({ name: "", description: "", category: "Main", price: "", currency: "USD", available: true });
  const [rejectReason, setRejectReason] = useState("");
  const refreshOwnedRestaurants = useCallback(async () => {
    const result = await onRun(() => api.myRestaurants());
    if (result) setOwnedRestaurants(result.items);
  }, [api, onRun]);
  useEffect(() => { void refreshOwnedRestaurants(); }, [refreshOwnedRestaurants]);
  const selectRestaurant = async (id: string) => {
    const trimmed = id.trim();
    if (!/^[a-fA-F0-9]{24}$/.test(trimmed)) return;
    setRestaurantId(trimmed);
    setRestaurant(null);
    setMenu([]);
    const loaded = await onRun(() => api.restaurant(trimmed));
    if (loaded) {
      setRestaurant(loaded);
      setRestaurantForm({
        name: loaded.name, description: loaded.description ?? "", cuisines: loaded.cuisines.join(", "),
        line1: loaded.address.line1, line2: loaded.address.line2 ?? "", city: loaded.address.city,
        region: loaded.address.region, postalCode: loaded.address.postalCode, countryCode: loaded.address.countryCode,
      });
      const menuPage = await onRun(() => api.menu(trimmed));
      if (menuPage) setMenu(menuPage.items);
    }
    const result = await onRun(() => api.restaurantOrders(trimmed));
    if (result) {
      setOrders(result.items);
      setOrderPage(result.page);
      setOrderPages(result.totalPages);
    }
  };
  const loadRestaurant = async (event: FormEvent) => {
    event.preventDefault();
    await selectRestaurant(restaurantId);
  };
  const restaurantBody = () => ({
    name: restaurantForm.name.trim(),
    description: restaurantForm.description.trim(),
    cuisines: restaurantForm.cuisines.split(",").map((part) => part.trim()).filter(Boolean),
    address: {
      line1: restaurantForm.line1.trim(),
      line2: restaurantForm.line2.trim() || null,
      city: restaurantForm.city.trim(),
      region: restaurantForm.region.trim(),
      postalCode: restaurantForm.postalCode.trim(),
      countryCode: restaurantForm.countryCode.toUpperCase().trim(),
    },
  });
  const saveRestaurant = async (event: FormEvent) => {
    event.preventDefault();
    const existingId = restaurantId.trim();
    const result = await onRun(() => /^[a-fA-F0-9]{24}$/.test(existingId)
      ? api.updateRestaurant(existingId, restaurantBody())
      : api.createRestaurant(restaurantBody()));
    if (result) {
      setRestaurant(result);
      setRestaurantId(result.id);
      setOwnedRestaurants((items) => {
        const next = items.filter((item) => item.id !== result.id);
        return [result, ...next];
      });
      await refreshOwnedRestaurants();
    }
  };
  const saveMenuItem = async (event: FormEvent) => {
    event.preventDefault();
    if (!restaurantId) return;
    const body = { ...itemForm, price: itemForm.price.trim() };
    const result = await onRun(() => editingMenuId
      ? api.updateMenuItem(editingMenuId, body)
      : api.createMenuItem(restaurantId, body));
    if (result) {
      setMenu((existing) => editingMenuId
        ? existing.map((item) => item.id === result.id ? result : item)
        : [...existing, result]);
      setEditingMenuId(null);
      setItemForm({ name: "", description: "", category: "Main", price: "", currency: "USD", available: true });
    }
  };
  const editMenuItem = (item: MenuItem) => {
    setEditingMenuId(item.id);
    setItemForm({
      name: item.name,
      description: item.description ?? "",
      category: item.category,
      price: item.price,
      currency: item.currency,
      available: item.available,
    });
  };
  const changeOrder = async (order: Order, status: string) => {
    const result = await onRun(() => api.updateRestaurantOrder(order.id, status, status === "REJECTED" ? rejectReason : undefined));
    if (result) {
      setOrders((items) => items.map((item) => item.id === result.id ? result : item));
      setRejectReason("");
    }
  };
  const refreshMenu = async () => {
    if (!restaurantId) return;
    const result = await onRun(() => api.menu(restaurantId));
    if (result) setMenu(result.items);
  };

  return (
    <section className="workspace">
      <PageIntro eyebrow="FOR THE PEOPLE BEHIND THE PLATE" title="Restaurant studio" text="Keep your menu fresh and move each incoming order through the kitchen." />
      <div className="notice-card"><strong>Owner workspace is live.</strong><span>Your restaurants are loaded from the authenticated owner endpoint. Pick one to jump in, or create a new kitchen profile to manage its menu and orders.</span></div>
      <div className="surface-card id-lookup-card">
        <form className="lookup-form" onSubmit={(event) => void loadRestaurant(event)}>
          <label className="field"><span>Restaurant ID</span><input value={restaurantId} onChange={(event) => setRestaurantId(event.target.value)} placeholder="24-character restaurant ID" pattern="[a-fA-F0-9]{24}" /></label>
          <button className="button button-dark" disabled={busy}>Load restaurant workspace</button>
        </form>
        {ownedRestaurants.length > 0 && (
          <div className="owned-restaurant-list">
            <span className="eyebrow">YOUR KITCHENS</span>
            <div className="chip-row">
              {ownedRestaurants.map((item) => (
                <button key={item.id} type="button" className={`chip ${restaurantId === item.id ? "active" : ""}`} onClick={() => void selectRestaurant(item.id)}>
                  {item.name}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
      <div className="workspace-grid owner-grid">
        <form className="surface-card form-card" onSubmit={(event) => void saveRestaurant(event)}>
          <div className="section-heading compact-heading"><div><span className="eyebrow">{/^[a-fA-F0-9]{24}$/.test(restaurantId) ? "EDIT RESTAURANT" : "START A KITCHEN"}</span><h2>{restaurant?.name ?? "Restaurant profile"}</h2></div>{restaurant && <span className="status-pill">{prettyStatus(restaurant.status)}</span>}</div>
          <div className="form-grid">
            <Field label="Restaurant name" value={restaurantForm.name} onChange={(name) => setRestaurantForm({ ...restaurantForm, name })} required />
            <Field label="Cuisines · comma-separated" value={restaurantForm.cuisines} onChange={(cuisines) => setRestaurantForm({ ...restaurantForm, cuisines })} required />
            <Field label="Description" value={restaurantForm.description} onChange={(description) => setRestaurantForm({ ...restaurantForm, description })} />
            <Field label="Street address" value={restaurantForm.line1} onChange={(line1) => setRestaurantForm({ ...restaurantForm, line1 })} required />
            <Field label="Address line 2" value={restaurantForm.line2} onChange={(line2) => setRestaurantForm({ ...restaurantForm, line2 })} />
            <Field label="City" value={restaurantForm.city} onChange={(city) => setRestaurantForm({ ...restaurantForm, city })} required />
            <Field label="Region / state" value={restaurantForm.region} onChange={(region) => setRestaurantForm({ ...restaurantForm, region })} required />
            <Field label="Postal code" value={restaurantForm.postalCode} onChange={(postalCode) => setRestaurantForm({ ...restaurantForm, postalCode })} required />
            <Field label="Country code" value={restaurantForm.countryCode} onChange={(countryCode) => setRestaurantForm({ ...restaurantForm, countryCode })} required maxLength={2} />
          </div>
          <button className="button button-primary" disabled={busy}>{/^[a-fA-F0-9]{24}$/.test(restaurantId) ? "Save restaurant" : "Create restaurant"}</button>
        </form>
        <form className="surface-card form-card" onSubmit={(event) => void saveMenuItem(event)}>
          <div className="section-heading compact-heading"><div><span className="eyebrow">MENU BUILDER</span><h2>{editingMenuId ? "Edit menu item" : "Add a menu item"}</h2></div></div>
          <Field label="Item name" value={itemForm.name} onChange={(name) => setItemForm({ ...itemForm, name })} required />
          <Field label="Description" value={itemForm.description} onChange={(description) => setItemForm({ ...itemForm, description })} />
          <div className="form-grid">
            <Field label="Category" value={itemForm.category} onChange={(category) => setItemForm({ ...itemForm, category })} required />
            <Field label="Price · USD" value={itemForm.price} onChange={(price) => setItemForm({ ...itemForm, price })} required type="number" min="0" step="0.01" />
          </div>
          <label className="checkbox-field"><input type="checkbox" checked={itemForm.available} onChange={(event) => setItemForm({ ...itemForm, available: event.target.checked })} /> Available to order</label>
          <div className="form-actions"><button className="button button-primary" disabled={busy || !restaurantId}>{editingMenuId ? "Save menu changes" : "Add to menu"}</button>{editingMenuId && <button className="button button-outline" type="button" onClick={() => { setEditingMenuId(null); setItemForm({ name: "", description: "", category: "Main", price: "", currency: "USD", available: true }); }}>Cancel</button>}</div>
          {!restaurantId && <small className="form-hint">Load or create a restaurant to manage its menu.</small>}
          <div className="menu-management">
            <div className="tracking-heading"><strong>Current menu</strong><button type="button" className="text-button" onClick={() => void refreshMenu()} disabled={!restaurantId}>Refresh</button></div>
            {menu.map((item) => <div className="managed-item" key={item.id}><div><strong>{item.name}</strong><small>{formatMoney(item.price)} · {item.available ? "Available" : "Unavailable"}</small></div><div><button className="text-button" type="button" onClick={() => editMenuItem(item)}>Edit</button><button className="text-button" type="button" onClick={() => void onRun(async () => { const updated = await api.setMenuAvailability(item.id, !item.available); setMenu((items) => items.map((candidate) => candidate.id === item.id ? updated : candidate)); })}>{item.available ? "Pause" : "Publish"}</button></div></div>)}
          </div>
        </form>
      </div>
      <section className="surface-card owner-orders">
        <div className="section-heading compact-heading"><div><span className="eyebrow">THE KITCHEN QUEUE</span><h2>Incoming orders</h2></div><span className="result-count">{orders.length} orders</span></div>
        {!restaurantId ? <EmptyState title="Load a restaurant to view orders" text="Order Service checks your ownership before returning anything." /> : orders.length === 0 ? <EmptyState title="No orders in the queue" text="New customer orders will appear here." /> : orders.map((order) => (
          <article className="owner-order-row" key={order.id}>
            <div className="owner-order-main"><strong>#{order.id.slice(-6).toUpperCase()} · {prettyStatus(order.status)}</strong><small>{formatDate(order.createdAt)} · {order.items.map((item) => `${item.quantity} × ${item.name}`).join(", ")}</small></div>
            <strong>{formatMoney(order.total)}</strong>
            {order.status === "PLACED" && <div className="owner-order-actions"><button className="button button-primary button-small" disabled={busy} onClick={() => void changeOrder(order, "CONFIRMED")}>Accept</button><input aria-label="Rejection reason" placeholder="Reason to reject" value={rejectReason} onChange={(event) => setRejectReason(event.target.value)} /><button className="button button-outline button-small" disabled={busy || !rejectReason.trim()} onClick={() => void changeOrder(order, "REJECTED")}>Reject</button></div>}
            {order.status === "CONFIRMED" && <button className="button button-outline button-small" disabled={busy} onClick={() => void changeOrder(order, "PREPARING")}>Start preparing</button>}
            {order.status === "PREPARING" && <button className="button button-primary button-small" disabled={busy || order.deliveryProvisioningStatus !== "COMPLETE"} title={order.deliveryProvisioningStatus !== "COMPLETE" ? "Delivery provisioning must complete first" : ""} onClick={() => void changeOrder(order, "READY_FOR_PICKUP")}>Ready for pickup</button>}
            {order.deliveryProvisioningStatus !== "NOT_REQUESTED" && <small className="provision-status">Delivery: {prettyStatus(order.deliveryProvisioningStatus)}{order.deliveryId ? ` · ${order.deliveryId}` : ""}</small>}
          </article>
        ))}
        {orderPages > 1 && <div className="pagination"><button className="button button-outline button-small" disabled={orderPage === 0 || busy} onClick={() => void onRun(async () => { const result = await api.restaurantOrders(restaurantId, orderPage - 1); setOrders(result.items); setOrderPage(result.page); setOrderPages(result.totalPages); })}>Previous</button><span>{orderPage + 1} / {orderPages}</span><button className="button button-outline button-small" disabled={orderPage + 1 >= orderPages || busy} onClick={() => void onRun(async () => { const result = await api.restaurantOrders(restaurantId, orderPage + 1); setOrders(result.items); setOrderPage(result.page); setOrderPages(result.totalPages); })}>Next</button></div>}
      </section>
    </section>
  );
}

function DeliveryView({ api, busy, onRun }: { api: Api; busy: boolean; onRun: Run }) {
  const [lookupId, setLookupId] = useState("");
  const [lookupType, setLookupType] = useState<"order" | "delivery">("order");
  const [delivery, setDelivery] = useState<Delivery | null>(null);
  const [assignments, setAssignments] = useState<Delivery[]>([]);
  const [partnerId, setPartnerId] = useState("demo-partner-01");
  const [loading, setLoading] = useState(false);
  const [events, setEvents] = useState<Delivery["events"]>([]);
  const refreshAssignments = useCallback(async () => {
    const result = await onRun(() => api.myAssignments());
    if (result) setAssignments(result.items);
  }, [api, onRun]);
  useEffect(() => { void refreshAssignments(); }, [refreshAssignments]);
  const load = async (event: FormEvent) => {
    event.preventDefault();
    setLoading(true);
    const result = await onRun(() => lookupType === "order" ? api.deliveryByOrder(lookupId.trim()) : api.delivery(lookupId.trim()));
    if (result) {
      setDelivery(result);
      setEvents(result.events);
      setLookupId(lookupType === "order" ? result.orderId : result.id);
    }
    setLoading(false);
  };
  const refresh = async (id = delivery?.id) => {
    if (!id) return;
    const result = await onRun(() => api.delivery(id));
    if (result) {
      setDelivery(result);
      setEvents(result.events);
    }
  };
  const timeline = async () => {
    if (!delivery) return;
    const result = await onRun(() => api.deliveryTimeline(delivery.id));
    if (result) setEvents(result.events);
  };
  const update = async (action: () => Promise<Delivery>) => {
    const result = await onRun(action);
    if (result) {
      setDelivery(result);
      setEvents(result.events);
      await refreshAssignments();
    }
  };

  return (
    <section className="workspace">
      <PageIntro eyebrow="DELIVERY OPERATIONS" title="Delivery desk" text="Look up a delivery, assign its configured demo partner, and record the handoff milestones." />
      <div className="notice-card"><strong>Assigned delivery queue is available.</strong><span>Use your operator assignment list to jump into active work, or look up a delivery by order ID or delivery ID. Assignment and status changes are authorized by Delivery Service; real courier dispatch and GPS are not included.</span></div>
      {assignments.length > 0 && (
        <div className="surface-card assignment-list">
          <div className="section-heading compact-heading"><div><span className="eyebrow">YOUR ASSIGNMENTS</span><h2>{assignments.length} active</h2></div></div>
          <div className="list-stack">
            {assignments.map((item) => (
              <button key={item.id} type="button" className="secondary-list-item" onClick={() => { setDelivery(item); setEvents(item.events); setLookupId(item.id); setLookupType("delivery"); }}>
                <div><strong>{prettyStatus(item.status)}</strong><small>Order {item.orderId}</small></div>
                <span>{item.partner?.displayName ?? "Partner pending"}</span>
              </button>
            ))}
          </div>
        </div>
      )}
      <form className="surface-card lookup-form delivery-lookup" onSubmit={(event) => void load(event)}>
        <label className="field"><span>Find delivery by</span><select value={lookupType} onChange={(event) => setLookupType(event.target.value as "order" | "delivery")}><option value="order">Order ID</option><option value="delivery">Delivery ID</option></select></label>
        <label className="field lookup-id"><span>{lookupType === "order" ? "Order ID" : "Delivery ID"}</span><input value={lookupId} onChange={(event) => setLookupId(event.target.value)} required pattern="[a-fA-F0-9]{24}" placeholder="24-character ID" /></label>
        <button className="button button-dark" disabled={busy || loading}>{loading ? "Looking…" : "Find delivery"}</button>
      </form>
      {delivery && (
        <section className="surface-card delivery-detail">
          <div className="detail-heading"><div><span className="eyebrow">DELIVERY {delivery.id.slice(-6).toUpperCase()}</span><h2>{prettyStatus(delivery.status)}</h2><small>Order {delivery.orderId}</small></div><button className="button button-outline button-small" onClick={() => void refresh()}>Refresh status</button></div>
          <div className="delivery-facts"><div><span className="eyebrow">PICKUP</span><p>{delivery.pickupAddress.line1}<br />{delivery.pickupAddress.city}, {delivery.pickupAddress.region}</p></div><div><span className="eyebrow">DROP-OFF</span><p>{delivery.deliveryAddress.line1}<br />{delivery.deliveryAddress.city}, {delivery.deliveryAddress.region}</p></div><div><span className="eyebrow">PARTNER</span><p>{delivery.partner?.displayName ?? "Not assigned"}</p></div></div>
          <div className="tracking-heading"><h3>Delivery timeline</h3><button className="text-button" onClick={() => void timeline()}>Refresh timeline</button></div>
          <ol className="timeline">{events.map((event) => <li key={event.id}><i /><div><strong>{prettyStatus(event.type)}</strong><small>{formatDate(event.occurredAt)} · {event.source}{event.callbackPending ? " · callback pending" : ""}</small></div></li>)}</ol>
          {delivery.orderCallbackPending && <p className="inline-note">Delivery status is saved; synchronization to the Order Service is pending.</p>}
          <div className="delivery-actions">
            {delivery.status === "PENDING_ASSIGNMENT" && <form className="inline-form" onSubmit={(event) => { event.preventDefault(); void update(() => api.assignDelivery(delivery.id, partnerId)); }}><label className="field"><span>Configured partner ID</span><input value={partnerId} onChange={(event) => setPartnerId(event.target.value)} required /></label><button className="button button-primary" disabled={busy}>Assign partner</button></form>}
            {delivery.status === "ASSIGNED" && <button className="button button-primary" disabled={busy} onClick={() => void update(() => api.updateDeliveryStatus(delivery.id, "PICKED_UP"))}>Record pickup</button>}
            {delivery.status === "PICKED_UP" && <button className="button button-primary" disabled={busy} onClick={() => void update(() => api.updateDeliveryStatus(delivery.id, "DELIVERED"))}>Mark delivered</button>}
            {["DELIVERED", "CANCELLED"].includes(delivery.status) && <span className="inline-note">This delivery is complete.</span>}
          </div>
        </section>
      )}
      {!delivery && <div className="surface-card empty-workspace"><EmptyState title="Ready when you are" text="Enter an order or delivery ID to load a real delivery record." /></div>}
    </section>
  );
}

function AdminView({ api, busy, onRun }: { api: Api; busy: boolean; onRun: Run }) {
  const [orderId, setOrderId] = useState("");
  const [reason, setReason] = useState("");
  const [result, setResult] = useState<Order | null>(null);
  const retry = async (event: FormEvent) => {
    event.preventDefault();
    const response = await onRun(() => api.retryProvisioning(orderId.trim()));
    if (response) setResult(response);
  };
  const cancel = async () => {
    if (!reason.trim()) return;
    const response = await onRun(() => api.adminCancelOrder(orderId.trim(), reason.trim()));
    if (response) setResult(response);
  };
  return (
    <section className="workspace">
      <PageIntro eyebrow="PLATFORM OPERATIONS" title="Order operations" text="Recover delivery provisioning or coordinate an eligible pre-pickup cancellation." />
      <div className="notice-card"><strong>Admin access is enforced by each service.</strong><span>This screen exposes only the documented retry and coordinated cancellation APIs. It cannot grant roles; an administrator account must be provisioned outside public registration.</span></div>
      <form className="surface-card admin-form" onSubmit={(event) => void retry(event)}>
        <div className="section-heading compact-heading"><div><span className="eyebrow">SAFE, IDEMPOTENT RECOVERY</span><h2>Resolve an order</h2></div></div>
        <label className="field"><span>Order ID</span><input value={orderId} onChange={(event) => setOrderId(event.target.value)} required pattern="[a-fA-F0-9]{24}" placeholder="24-character order ID" /></label>
        <label className="field"><span>Cancellation reason · required for cancellation</span><input value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} /></label>
        <div className="form-actions"><button className="button button-primary" disabled={busy || !orderId.trim()}>Retry delivery provisioning</button><button className="button button-outline danger-button" type="button" disabled={busy || !orderId.trim() || !reason.trim()} onClick={() => void cancel()}>Cancel before pickup</button></div>
        {result && <div className="operation-result"><span className="eyebrow">LATEST SERVICE RESPONSE</span><strong>Order {result.id}</strong><span>{prettyStatus(result.status)} · provisioning {prettyStatus(result.deliveryProvisioningStatus)} · cancellation {prettyStatus(result.cancellationStatus)}</span>{result.deliveryId && <small>Delivery ID: {result.deliveryId}</small>}</div>}
      </form>
    </section>
  );
}

function AuthPanel({ onLogin, onRegister, onValidation, onClose, busy }: {
  onLogin: (event: FormEvent<HTMLFormElement>, credentials: { email: string; password: string }) => void;
  onRegister: (event: FormEvent<HTMLFormElement>, input: { email: string; password: string; displayName: string }) => void;
  onValidation: (message: string) => void;
  onClose: () => void;
  busy: boolean;
}) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const submit = (event: FormEvent<HTMLFormElement>) => {
    if (mode === "login") {
      event.preventDefault();
      const emailInput = event.currentTarget.querySelector<HTMLInputElement>('input[type="email"]');
      const message = !emailInput?.checkValidity()
        ? "Enter a valid email address."
        : !password
          ? "Enter your password."
          : null;
      if (message) {
        onValidation(message);
        return;
      }
      onLogin(event, { email, password });
      return;
    }

    event.preventDefault();
    const emailInput = event.currentTarget.querySelector<HTMLInputElement>('input[type="email"]');
    const message = !displayName.trim()
      ? "Enter your name to create an account."
      : !emailInput?.checkValidity()
        ? "Enter a valid email address."
        : password.length < 12
          ? "Password must be at least 12 characters."
          : password.length > 128
            ? "Password must be 128 characters or fewer."
            : !event.currentTarget.checkValidity()
              ? "Complete all required fields to create an account."
              : null;
    if (message) {
      onValidation(message);
      return;
    }
    onRegister(event, { email, password, displayName });
  };
  return (
    <div className="auth-card" role="dialog" aria-label="Sign in or register">
      <button className="modal-close auth-close" onClick={onClose} aria-label="Close sign in">×</button>
      <span className="eyebrow">YOUR TABLE IS WAITING</span>
      <h2>{mode === "login" ? "Sign in for the good stuff." : "A fresh start tastes good."}</h2>
      <p>{mode === "login" ? "Use your customer account to save addresses and place an order." : "Create a customer account. New public accounts always receive the CUSTOMER role."}</p>
      <form noValidate onSubmit={submit}>
        {mode === "register" && <Field label="Your name" value={displayName} onChange={setDisplayName} required maxLength={100} />}
        <Field label="Email" type="email" value={email} onChange={setEmail} required maxLength={254} />
        <Field label="Password" type="password" value={password} onChange={setPassword} required minLength={mode === "register" ? 12 : 1} maxLength={128} />
        {mode === "register" && <small className="form-hint">Use at least 12 characters.</small>}
        <button className="button button-primary auth-submit" disabled={busy}>{busy ? "Working…" : mode === "login" ? "Sign in" : "Create customer account"}</button>
      </form>
      <button className="text-button auth-switch" onClick={() => setMode(mode === "login" ? "register" : "login")}>{mode === "login" ? "New to FoodieExpress? Create an account" : "Already have an account? Sign in"}</button>
    </div>
  );
}

function Toast({ toast, onDismiss }: { toast: ToastMessage; onDismiss: (id: number) => void }) {
  useEffect(() => {
    const timer = window.setTimeout(() => onDismiss(toast.id), 3500);
    return () => window.clearTimeout(timer);
  }, [onDismiss, toast.id]);

  return (
    <div className={`toast toast-${toast.kind}`} role={toast.kind === "error" ? "alert" : "status"}>
      <span>{toast.message}</span>
      <button onClick={() => onDismiss(toast.id)} aria-label="Dismiss notification">×</button>
    </div>
  );
}

function PageIntro({ eyebrow, title, text }: { eyebrow: string; title: string; text: string }) {
  return <div className="page-intro"><span className="eyebrow">{eyebrow}</span><h1>{title}</h1><p>{text}</p></div>;
}

function EmptyState({ title, text }: { title: string; text: string }) {
  return <div className="empty-state"><span className="empty-icon">✳</span><strong>{title}</strong><p>{text}</p></div>;
}

function Loading({ label }: { label: string }) {
  return <div className="loading-state" role="status"><span className="spinner" />{label}</div>;
}

function Field({ label, value, onChange, ...props }: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  required?: boolean;
  onBlur?: () => void;
  minLength?: number;
  maxLength?: number;
  min?: string;
  step?: string;
  "aria-invalid"?: boolean;
}) {
  const id = useMemo(() => `field-${label.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`, [label]);
  return <label className="field" htmlFor={id}><span>{label}</span><input id={id} value={value} onChange={(event) => onChange(event.target.value)} {...props} /></label>;
}

function validateAddress(value: AddressInput): Partial<Record<keyof AddressInput, string>> {
  const errors: Partial<Record<keyof AddressInput, string>> = {};
  if (!value.label.trim()) errors.label = "Enter an address label.";
  if (!value.recipientName.trim()) errors.recipientName = "Enter the recipient name.";
  if (!value.line1.trim()) errors.line1 = "Enter the street address.";
  if (!value.city.trim()) errors.city = "Enter the city.";
  if (!indianRegions.includes(value.region)) errors.region = "Choose an Indian state or union territory.";
  if (!/^\d{6}$/.test(value.postalCode.trim())) errors.postalCode = "Enter a six-digit Indian PIN code.";
  if (!normalizeIndianPhone(value.phone)) errors.phone = "Enter a valid 10-digit Indian mobile number.";
  return errors;
}

function normalizeIndianPhone(phone: string): string | null {
  const match = /^(?:\+91[ -]?)?([6-9](?:[ -]?\d){9})$/.exec(phone.trim());
  if (!match) return null;
  const nationalDigits = match[1].replace(/[ -]/g, "");
  return nationalDigits.length === 10 ? `+91${nationalDigits}` : null;
}

function AddressFields({
  value,
  onChange,
  onBlur,
  errors,
}: {
  value: AddressInput;
  onChange: (value: AddressInput, field: keyof AddressInput) => void;
  onBlur: (field: keyof AddressInput) => void;
  errors: Partial<Record<keyof AddressInput, string>>;
}) {
  const update = (key: keyof AddressInput, next: string) => onChange({ ...value, [key]: next }, key);
  const showError = (key: keyof AddressInput) => errors[key] && <small className="field-error">{errors[key]}</small>;
  const blur = (key: keyof AddressInput) => () => onBlur(key);
  return (
    <div className="form-grid address-form-grid">
      <div><Field label="Address label" value={value.label} onChange={(next) => update("label", next)} onBlur={blur("label")} required maxLength={40} aria-invalid={Boolean(errors.label)} />{showError("label")}</div>
      <div><Field label="Recipient name" value={value.recipientName} onChange={(next) => update("recipientName", next)} onBlur={blur("recipientName")} required maxLength={100} aria-invalid={Boolean(errors.recipientName)} />{showError("recipientName")}</div>
      <div><Field label="Indian mobile number" type="tel" value={value.phone} onChange={(next) => update("phone", next)} onBlur={blur("phone")} required aria-invalid={Boolean(errors.phone)} />{showError("phone")}</div>
      <div><Field label="Street address" value={value.line1} onChange={(next) => update("line1", next)} onBlur={blur("line1")} required maxLength={150} aria-invalid={Boolean(errors.line1)} />{showError("line1")}</div>
      <div><Field label="Address line 2" value={value.line2 ?? ""} onChange={(next) => update("line2", next)} onBlur={blur("line2")} maxLength={150} /></div>
      <div><Field label="City" value={value.city} onChange={(next) => update("city", next)} onBlur={blur("city")} required maxLength={100} aria-invalid={Boolean(errors.city)} />{showError("city")}</div>
      <div>
        <label className="field"><span>State / union territory</span>
          <select value={value.region} onChange={(event) => update("region", event.target.value)} onBlur={blur("region")} aria-invalid={Boolean(errors.region)} required>
            <option value="">Choose state or union territory</option>
            {indianRegions.map((region) => <option key={region} value={region}>{region}</option>)}
          </select>
        </label>
        {showError("region")}
      </div>
      <div><Field label="PIN code" value={value.postalCode} onChange={(next) => update("postalCode", next)} onBlur={blur("postalCode")} required maxLength={6} aria-invalid={Boolean(errors.postalCode)} />{showError("postalCode")}</div>
      <label className="field"><span>Country</span><input value="India" readOnly /></label>
    </div>
  );
}

function prettyStatus(status: string) {
  return status.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function messageFor(reason: unknown) {
  if (reason instanceof ApiRequestError) {
    const fields = reason.fieldErrors?.map((field) => `${field.field}: ${field.message}`).join("; ");
    if (reason.status === 403) return "Your account does not have permission for this action.";
    return `${reason.message}${fields ? ` — ${fields}` : ""}`;
  }
  return reason instanceof Error ? reason.message : "Something went wrong. Please try again.";
}

export default function App() {
  return <AppContent />;
}

function AppContent() {
  const [authMode, setAuthMode] = useState(false);
  const [token, setToken] = useState(() => sessionStorage.getItem("foodieexpress.accessToken"));
  const [user, setUser] = useState<User | null>(null);
  const [checkingSession, setCheckingSession] = useState(Boolean(sessionStorage.getItem("foodieexpress.accessToken")));
  const [view, setView] = useState<View>("restaurants");
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const [busy, setBusy] = useState(false);
  const dispatch = useDispatch<AppDispatch>();
  const cart = useSelector((state: RootState) => state.cart);
  const checkoutIdentity = useRef<{ fingerprint: string; key: string } | null>(null);
  const nextToastId = useRef(0);
  const showToast = useCallback((message: string, kind: ToastKind) => {
    const id = ++nextToastId.current;
    setToasts((current) => [...current, { id, message, kind }]);
  }, []);
  const dismissToast = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const expireSession = useCallback(() => {
    sessionStorage.removeItem("foodieexpress.accessToken");
    setToken(null);
    setUser(null);
    setCheckingSession(false);
    showToast("Your session expired. Sign in again to continue.", "warning");
  }, [showToast]);
  const api = useMemo(() => createApi(token, expireSession), [token, expireSession]);

  useEffect(() => {
    if (!token) {
      setCheckingSession(false);
      setUser(null);
      return;
    }
    const expires = tokenExpiry(token);
    if (expires !== null && expires <= Date.now()) {
      expireSession();
      return;
    }
    const timer = expires === null ? undefined : window.setTimeout(expireSession, expires - Date.now());
    api.profile()
      .then((profile) => {
        setUser(profile);
        setAuthMode(false);
      })
      .catch((reason: unknown) => {
        if (!(reason instanceof ApiRequestError && reason.unauthorizedHandled)) {
          showToast(messageFor(reason), "error");
        }
      })
      .finally(() => setCheckingSession(false));
    return () => {
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [api, expireSession, showToast, token]);

  useEffect(() => {
    const syncRoute = () => {
      const path = window.location.hash.replace(/^#\/?/, "") as View;
      const next = Object.hasOwn(viewTitles, path) ? path : "restaurants";
      if (!canAccessView(next, user)) {
        if (checkingSession) {
          setView(next);
          return;
        }
        const requiredRole = rolesForView[next] ?? (next === "delivery" ? "DELIVERY_OPERATOR" : null);
        if (user && requiredRole) showToast(`This view requires the ${requiredRole.replaceAll("_", " ").toLowerCase()} role.`, "warning");
        setView("restaurants");
        if (window.location.hash) window.history.replaceState(null, "", "#/restaurants");
        return;
      }
      setView(next);
    };
    window.addEventListener("hashchange", syncRoute);
    syncRoute();
    return () => window.removeEventListener("hashchange", syncRoute);
  }, [checkingSession, showToast, user]);

  const run = useCallback(async <T,>(action: () => Promise<T>): Promise<T | undefined> => {
    setBusy(true);
    try {
      return await action();
    } catch (reason) {
      if (!(reason instanceof ApiRequestError && reason.unauthorizedHandled)) {
        showToast(messageFor(reason), "error");
      }
      return undefined;
    } finally {
      setBusy(false);
    }
  }, [showToast]);

  const signOut = () => {
    sessionStorage.removeItem("foodieexpress.accessToken");
    setToken(null);
    setUser(null);
    dispatch(clearCart());
    setView("restaurants");
    setAuthMode(false);
    window.location.hash = "#/restaurants";
    showToast("You have been signed out.", "success");
  };
  const navigate = (next: View) => {
    if (!canAccessView(next, user)) {
      const requiredRole = rolesForView[next] ?? (next === "delivery" ? "DELIVERY_OPERATOR" : null);
      if (requiredRole) showToast(`This view requires the ${requiredRole.replaceAll("_", " ").toLowerCase()} role.`, "warning");
      return;
    }
    window.location.hash = `#/${next}`;
  };
  const handleLogin = async (event: FormEvent<HTMLFormElement>, credentials: { email: string; password: string }) => {
    event.preventDefault();
    const result = await run(() => api.login(credentials));
    if (!result) return;
    sessionStorage.setItem("foodieexpress.accessToken", result.accessToken);
    setToken(result.accessToken);
    setUser(result.user);
    setAuthMode(false);
    showToast(`Welcome back, ${result.user.displayName}.`, "success");
  };
  const handleRegister = async (event: FormEvent<HTMLFormElement>, input: { email: string; password: string; displayName: string }) => {
    event.preventDefault();
    setBusy(true);
    try {
      await api.register(input);
      setAuthMode(false);
      showToast("Customer account created successfully. Sign in to start ordering.", "success");
    } catch (reason) {
      showToast(messageFor(reason), "error");
    } finally {
      setBusy(false);
    }
  };
  const customer = user?.roles.includes("CUSTOMER") ?? false;
  const owner = user?.roles.includes("RESTAURANT_OWNER") ?? false;
  const operator = user?.roles.includes("DELIVERY_OPERATOR") ?? false;
  const admin = user?.roles.includes("ADMIN") ?? false;

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#/restaurants" aria-label="FoodieExpress home"><span className="brand-mark">f</span><span>foodie<span className="brand-highlight">express</span></span></a>
        <nav className="main-nav" aria-label="Main navigation">
          <button className={view === "restaurants" ? "nav-link active" : "nav-link"} onClick={() => navigate("restaurants")}>Discover</button>
          {customer && <button className={view === "orders" ? "nav-link active" : "nav-link"} onClick={() => navigate("orders")}>Orders</button>}
          {customer && <button className={view === "addresses" ? "nav-link active" : "nav-link"} onClick={() => navigate("addresses")}>Addresses</button>}
          {owner && <button className={view === "owner" ? "nav-link active" : "nav-link"} onClick={() => navigate("owner")}>Restaurant studio</button>}
          {(operator || admin) && <button className={view === "delivery" ? "nav-link active" : "nav-link"} onClick={() => navigate("delivery")}>Delivery desk</button>}
          {admin && <button className={view === "admin" ? "nav-link active" : "nav-link"} onClick={() => navigate("admin")}>Operations</button>}
        </nav>
        <div className="account-actions">
          {user ? <><span className="user-greeting">Hi, {user.displayName.split(" ")[0]}</span><button className="button button-outline button-small" onClick={signOut}>Sign out</button></> : <button className="button button-outline button-small" onClick={() => setAuthMode(true)}>Sign in</button>}
        </div>
      </header>
      <div className="toast-stack" aria-label="Notifications">
        {toasts.map((toast) => <Toast key={toast.id} toast={toast} onDismiss={dismissToast} />)}
      </div>
      {checkingSession ? <main className="page-shell"><Loading label="Restoring your secure session" /></main> : (
        <main className="page-shell">
          {view === "restaurants" && (
            <DiscoveryView api={api} user={user} cart={cart} busy={busy} onRun={run}
              onAdd={(item) => {
                if (!customer) { setAuthMode(true); showToast("Sign in or create a customer account to add items to your cart.", "info"); return; }
                if (cart.length && cart[0].restaurantId !== item.restaurantId) {
                  if (!window.confirm("Your cart has items from another restaurant. Clear it and start a new cart?")) return;
                  dispatch(clearCart()); checkoutIdentity.current = null;
                }
                dispatch(addItem(item)); showToast(`${item.name} added to your cart.`, "success");
              }}
              onQuantity={(menuItemId, quantity) => dispatch(setQuantity({ menuItemId, quantity }))}
              onClear={() => { dispatch(clearCart()); checkoutIdentity.current = null; }}
              onCheckout={async (addressId) => {
                if (!cart.length) return;
                const body = { restaurantId: cart[0].restaurantId, items: cart.map(({ menuItemId, quantity }) => ({ menuItemId, quantity })), addressId };
                const fingerprint = JSON.stringify(body);
                if (!checkoutIdentity.current || checkoutIdentity.current.fingerprint !== fingerprint) checkoutIdentity.current = { fingerprint, key: crypto.randomUUID() };
                const order = await run(() => api.createOrder(body, checkoutIdentity.current!.key));
                if (order) {
                  dispatch(clearCart()); checkoutIdentity.current = null; showToast(`Order placed. Your order number is ${order.id}.`, "success");
                  setView("orders"); window.location.hash = "#/orders";
                }
              }} />
          )}
          {view === "addresses" && customer && <AddressesView api={api} busy={busy} onRun={run} onNotify={showToast} />}
          {view === "orders" && customer && <OrdersView api={api} busy={busy} onRun={run} />}
          {view === "owner" && owner && <OwnerView api={api} busy={busy} onRun={run} />}
          {view === "delivery" && (operator || admin) && <DeliveryView api={api} busy={busy} onRun={run} />}
          {view === "admin" && admin && <AdminView api={api} busy={busy} onRun={run} />}
        </main>
      )}
      <footer className="site-footer"><span className="footer-brand"><img src="/favicon.svg" alt="" />FoodieExpress</span><span>Online ordering · simulated payment · GPS tracking unavailable</span></footer>
      {authMode && !user && <div className="modal-backdrop auth-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setAuthMode(false); }}><AuthPanel onLogin={handleLogin} onRegister={handleRegister} onValidation={(message) => showToast(message, "warning")} onClose={() => setAuthMode(false)} busy={busy} /></div>}
    </div>
  );
}
