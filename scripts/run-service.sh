#!/bin/bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    printf '%s\n' "Usage: scripts/run-service.sh user-service|restaurant-service|order-service|delivery-service" >&2
    exit 2
fi

case "$1" in
    user-service|restaurant-service|order-service|delivery-service)
        service="$1"
        ;;
    *)
        printf 'Unknown service: %s\n' "$1" >&2
        exit 2
        ;;
esac

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
secret_file="${HOME:?HOME must be set}/.config/foodieexpress/jwt-secret.b64"
if [[ ! -r "$secret_file" ]]; then
    printf '%s\n' "Local JWT secret is missing; run scripts/setup-local.sh from the repository root first." >&2
    exit 1
fi

if ! decoded_bytes=$(openssl base64 -d -A -in "$secret_file" 2>/dev/null | wc -c | tr -d '[:space:]'); then
    printf '%s\n' "The local JWT secret is not valid Base64; run scripts/setup-local.sh to diagnose it." >&2
    exit 1
fi
if [[ "$decoded_bytes" -lt 32 ]]; then
    printf '%s\n' "The local JWT secret is shorter than 256 bits; refusing to start the service." >&2
    exit 1
fi

export JWT_SECRET
JWT_SECRET="$(tr -d '\r\n' < "$secret_file")"

if [[ "$service" == "restaurant-service" || "$service" == "order-service" ]]; then
    internal_public_key_file="${HOME:?HOME must be set}/.config/foodieexpress/order-restaurant-internal-public-key.b64"
    if [[ ! -r "$internal_public_key_file" ]]; then
        printf '%s\n' "Order-to-Restaurant public key is missing; run scripts/setup-local.sh first." >&2
        exit 1
    fi
    if ! public_key_bytes=$(
        openssl base64 -d -A -in "$internal_public_key_file" 2>/dev/null | wc -c | tr -d '[:space:]'
    ) || [[ "$public_key_bytes" -lt 32 ]]; then
        printf '%s\n' "The Order-to-Restaurant public key is invalid; run scripts/setup-local.sh to diagnose it." >&2
        exit 1
    fi
    export ORDER_RESTAURANT_INTERNAL_PUBLIC_KEY_BASE64
    ORDER_RESTAURANT_INTERNAL_PUBLIC_KEY_BASE64="$(tr -d '\r\n' < "$internal_public_key_file")"
fi

if [[ "$service" == "order-service" ]]; then
    internal_private_key_file="${HOME:?HOME must be set}/.config/foodieexpress/order-restaurant-internal-private-key.b64"
    if [[ ! -r "$internal_private_key_file" ]]; then
        printf '%s\n' "Order-to-Restaurant private key is missing; run scripts/setup-local.sh first." >&2
        exit 1
    fi
    if ! private_key_bytes=$(
        openssl base64 -d -A -in "$internal_private_key_file" 2>/dev/null | wc -c | tr -d '[:space:]'
    ) || [[ "$private_key_bytes" -lt 32 ]]; then
        printf '%s\n' "The Order-to-Restaurant private key is invalid; run scripts/setup-local.sh to diagnose it." >&2
        exit 1
    fi
    export ORDER_RESTAURANT_INTERNAL_PRIVATE_KEY_BASE64
    ORDER_RESTAURANT_INTERNAL_PRIVATE_KEY_BASE64="$(tr -d '\r\n' < "$internal_private_key_file")"

    user_internal_private_key_file="${HOME:?HOME must be set}/.config/foodieexpress/order-user-internal-private-key.b64"
    user_internal_public_key_file="${HOME:?HOME must be set}/.config/foodieexpress/order-user-internal-public-key.b64"
    if [[ ! -r "$user_internal_private_key_file" || ! -r "$user_internal_public_key_file" ]]; then
        printf '%s\n' "Order-to-User internal keys are missing; run scripts/setup-local.sh first." >&2
        exit 1
    fi
    export ORDER_USER_INTERNAL_PRIVATE_KEY_BASE64 ORDER_USER_INTERNAL_PUBLIC_KEY_BASE64
    ORDER_USER_INTERNAL_PRIVATE_KEY_BASE64="$(tr -d '\r\n' < "$user_internal_private_key_file")"
    ORDER_USER_INTERNAL_PUBLIC_KEY_BASE64="$(tr -d '\r\n' < "$user_internal_public_key_file")"
fi

if [[ "$service" == "user-service" ]]; then
    user_internal_public_key_file="${HOME:?HOME must be set}/.config/foodieexpress/order-user-internal-public-key.b64"
    if [[ ! -r "$user_internal_public_key_file" ]]; then
        printf '%s\n' "Order-to-User public key is missing; run scripts/setup-local.sh first." >&2
        exit 1
    fi
    export ORDER_USER_INTERNAL_PUBLIC_KEY_BASE64
    ORDER_USER_INTERNAL_PUBLIC_KEY_BASE64="$(tr -d '\r\n' < "$user_internal_public_key_file")"
fi

if [[ "$service" == "order-service" ]]; then
    order_delivery_private_key_file="${HOME:?HOME must be set}/.config/foodieexpress/order-delivery-internal-private-key.b64"
    order_delivery_public_key_file="${HOME:?HOME must be set}/.config/foodieexpress/order-delivery-internal-public-key.b64"
    delivery_order_public_key_file="${HOME:?HOME must be set}/.config/foodieexpress/delivery-order-internal-public-key.b64"
    if [[ ! -r "$order_delivery_private_key_file" || ! -r "$order_delivery_public_key_file" \
            || ! -r "$delivery_order_public_key_file" ]]; then
        printf '%s\n' "Order-to-Delivery internal keys are missing; run scripts/setup-local.sh first." >&2
        exit 1
    fi
    export ORDER_DELIVERY_INTERNAL_PRIVATE_KEY_BASE64 ORDER_DELIVERY_INTERNAL_PUBLIC_KEY_BASE64
    export DELIVERY_ORDER_INTERNAL_PUBLIC_KEY_BASE64
    ORDER_DELIVERY_INTERNAL_PRIVATE_KEY_BASE64="$(tr -d '\r\n' < "$order_delivery_private_key_file")"
    ORDER_DELIVERY_INTERNAL_PUBLIC_KEY_BASE64="$(tr -d '\r\n' < "$order_delivery_public_key_file")"
    DELIVERY_ORDER_INTERNAL_PUBLIC_KEY_BASE64="$(tr -d '\r\n' < "$delivery_order_public_key_file")"
fi

if [[ "$service" == "delivery-service" ]]; then
    order_delivery_public_key_file="${HOME:?HOME must be set}/.config/foodieexpress/order-delivery-internal-public-key.b64"
    delivery_order_private_key_file="${HOME:?HOME must be set}/.config/foodieexpress/delivery-order-internal-private-key.b64"
    delivery_order_public_key_file="${HOME:?HOME must be set}/.config/foodieexpress/delivery-order-internal-public-key.b64"
    if [[ ! -r "$order_delivery_public_key_file" || ! -r "$delivery_order_private_key_file" \
            || ! -r "$delivery_order_public_key_file" ]]; then
        printf '%s\n' "Delivery internal keys are missing; run scripts/setup-local.sh first." >&2
        exit 1
    fi
    export ORDER_DELIVERY_INTERNAL_PUBLIC_KEY_BASE64
    export DELIVERY_ORDER_INTERNAL_PRIVATE_KEY_BASE64 DELIVERY_ORDER_INTERNAL_PUBLIC_KEY_BASE64
    ORDER_DELIVERY_INTERNAL_PUBLIC_KEY_BASE64="$(tr -d '\r\n' < "$order_delivery_public_key_file")"
    DELIVERY_ORDER_INTERNAL_PRIVATE_KEY_BASE64="$(tr -d '\r\n' < "$delivery_order_private_key_file")"
    DELIVERY_ORDER_INTERNAL_PUBLIC_KEY_BASE64="$(tr -d '\r\n' < "$delivery_order_public_key_file")"
fi

cd "$repo_root/backend/$service"
exec mvn spring-boot:run
