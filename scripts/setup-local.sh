#!/bin/bash
set -euo pipefail

if ! command -v openssl >/dev/null 2>&1; then
    printf '%s\n' "OpenSSL is required to generate the local JWT secret." >&2
    exit 1
fi

config_dir="${HOME:?HOME must be set}/.config/foodieexpress"
mkdir -p "$config_dir"
chmod 700 "$config_dir"

ensure_secret() {
    local secret_file="$1"
    local description="$2"

    if [[ ! -e "$secret_file" ]]; then
        if ! (set -o noclobber; umask 077; openssl rand -base64 32 > "$secret_file"); then
            if [[ ! -e "$secret_file" ]]; then
                printf 'Could not create %s.\n' "$description" >&2
                exit 1
            fi
        fi
    fi

    chmod 600 "$secret_file"
    if [[ ! -s "$secret_file" ]]; then
        printf 'The %s file is empty; refusing to replace it.\n' "$description" >&2
        exit 1
    fi

    if ! decoded_bytes=$(openssl base64 -d -A -in "$secret_file" 2>/dev/null | wc -c | tr -d '[:space:]'); then
        printf 'The existing %s is not valid Base64; it was not changed.\n' "$description" >&2
        exit 1
    fi
    if [[ "$decoded_bytes" -lt 32 ]]; then
        printf 'The existing %s is shorter than 256 bits; it was not changed.\n' "$description" >&2
        exit 1
    fi
}

jwt_secret_file="$config_dir/jwt-secret.b64"
internal_private_key_file="$config_dir/order-restaurant-internal-private-key.b64"
internal_public_key_file="$config_dir/order-restaurant-internal-public-key.b64"
ensure_secret "$jwt_secret_file" "local JWT secret"

if [[ -e "$internal_private_key_file" || -e "$internal_public_key_file" ]]; then
    if [[ ! -e "$internal_private_key_file" || ! -e "$internal_public_key_file" ]]; then
        printf '%s\n' "The existing Order-to-Restaurant key pair is incomplete; it was not replaced." >&2
        exit 1
    fi
    chmod 600 "$internal_private_key_file" "$internal_public_key_file"
else
    keygen_dir="$(mktemp -d "$config_dir/.order-restaurant-keygen.XXXXXX")"
    if ! openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 \
            -out "$keygen_dir/private.pem" 2>/dev/null \
            || ! openssl pkcs8 -topk8 -nocrypt -in "$keygen_dir/private.pem" -outform DER 2>/dev/null \
                | openssl base64 -A > "$keygen_dir/private.b64" \
            || ! openssl pkey -in "$keygen_dir/private.pem" -pubout -outform DER 2>/dev/null \
                | openssl base64 -A > "$keygen_dir/public.b64"; then
        rm -f "$keygen_dir/private.pem" "$keygen_dir/private.b64" "$keygen_dir/public.b64"
        rmdir "$keygen_dir"
        printf '%s\n' "Could not create the Order-to-Restaurant ES256 key pair." >&2
        exit 1
    fi
    chmod 600 "$keygen_dir/private.b64" "$keygen_dir/public.b64"
    mv "$keygen_dir/private.b64" "$internal_private_key_file"
    mv "$keygen_dir/public.b64" "$internal_public_key_file"
    rm -f "$keygen_dir/private.pem"
    rmdir "$keygen_dir"
fi

for key_file in "$internal_private_key_file" "$internal_public_key_file"; do
    if ! decoded_bytes=$(openssl base64 -d -A -in "$key_file" 2>/dev/null | wc -c | tr -d '[:space:]'); then
        printf '%s\n' "The existing Order-to-Restaurant key pair is not valid Base64; it was not changed." >&2
        exit 1
    fi
    if [[ "$decoded_bytes" -lt 32 ]]; then
        printf '%s\n' "An Order-to-Restaurant key is invalid or too short; it was not changed." >&2
        exit 1
    fi
done

validation_dir="$(mktemp -d "$config_dir/.order-restaurant-validate.XXXXXX")"
if ! openssl base64 -d -A -in "$internal_private_key_file" -out "$validation_dir/private.der" 2>/dev/null \
        || ! openssl base64 -d -A -in "$internal_public_key_file" -out "$validation_dir/public.der" 2>/dev/null \
        || ! openssl pkey -inform DER -in "$validation_dir/private.der" \
            -pubout -outform DER -out "$validation_dir/derived-public.der" 2>/dev/null \
        || ! openssl pkey -pubin -inform DER -in "$validation_dir/public.der" -noout 2>/dev/null \
        || ! cmp -s "$validation_dir/derived-public.der" "$validation_dir/public.der"; then
    rm -f "$validation_dir/private.der" "$validation_dir/public.der" "$validation_dir/derived-public.der"
    rmdir "$validation_dir"
    printf '%s\n' "The existing Order-to-Restaurant keys are invalid or do not form a matching pair." >&2
    exit 1
fi
rm -f "$validation_dir/private.der" "$validation_dir/public.der" "$validation_dir/derived-public.der"
rmdir "$validation_dir"

user_private_key_file="$config_dir/order-user-internal-private-key.b64"
user_public_key_file="$config_dir/order-user-internal-public-key.b64"
if [[ -e "$user_private_key_file" || -e "$user_public_key_file" ]]; then
    if [[ ! -e "$user_private_key_file" || ! -e "$user_public_key_file" ]]; then
        printf '%s\n' "The existing Order-to-User key pair is incomplete; it was not replaced." >&2
        exit 1
    fi
    chmod 600 "$user_private_key_file" "$user_public_key_file"
else
    user_keygen_dir="$(mktemp -d "$config_dir/.order-user-keygen.XXXXXX")"
    if ! openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 \
            -out "$user_keygen_dir/private.pem" 2>/dev/null \
            || ! openssl pkcs8 -topk8 -nocrypt -in "$user_keygen_dir/private.pem" -outform DER 2>/dev/null \
                | openssl base64 -A > "$user_keygen_dir/private.b64" \
            || ! openssl pkey -in "$user_keygen_dir/private.pem" -pubout -outform DER 2>/dev/null \
                | openssl base64 -A > "$user_keygen_dir/public.b64"; then
        rm -f "$user_keygen_dir/private.pem" "$user_keygen_dir/private.b64" "$user_keygen_dir/public.b64"
        rmdir "$user_keygen_dir"
        printf '%s\n' "Could not create the Order-to-User ES256 key pair." >&2
        exit 1
    fi
    chmod 600 "$user_keygen_dir/private.b64" "$user_keygen_dir/public.b64"
    mv "$user_keygen_dir/private.b64" "$user_private_key_file"
    mv "$user_keygen_dir/public.b64" "$user_public_key_file"
    rm -f "$user_keygen_dir/private.pem"
    rmdir "$user_keygen_dir"
fi

user_validation_dir="$(mktemp -d "$config_dir/.order-user-validate.XXXXXX")"
if ! openssl base64 -d -A -in "$user_private_key_file" -out "$user_validation_dir/private.der" 2>/dev/null \
        || ! openssl base64 -d -A -in "$user_public_key_file" -out "$user_validation_dir/public.der" 2>/dev/null \
        || ! openssl pkey -inform DER -in "$user_validation_dir/private.der" \
            -pubout -outform DER -out "$user_validation_dir/derived-public.der" 2>/dev/null \
        || ! openssl pkey -pubin -inform DER -in "$user_validation_dir/public.der" -noout 2>/dev/null \
        || ! cmp -s "$user_validation_dir/derived-public.der" "$user_validation_dir/public.der"; then
    rm -f "$user_validation_dir/private.der" "$user_validation_dir/public.der" \
        "$user_validation_dir/derived-public.der"
    rmdir "$user_validation_dir"
    printf '%s\n' "The existing Order-to-User keys are invalid or do not form a matching pair." >&2
    exit 1
fi
rm -f "$user_validation_dir/private.der" "$user_validation_dir/public.der" \
    "$user_validation_dir/derived-public.der"
rmdir "$user_validation_dir"

ensure_internal_pair() {
    local name="$1"
    local private_key_file="$config_dir/$name-private-key.b64"
    local public_key_file="$config_dir/$name-public-key.b64"
    local work_dir

    if [[ -e "$private_key_file" || -e "$public_key_file" ]]; then
        if [[ ! -e "$private_key_file" || ! -e "$public_key_file" ]]; then
            printf 'The existing %s key pair is incomplete; it was not replaced.\n' "$name" >&2
            exit 1
        fi
        chmod 600 "$private_key_file" "$public_key_file"
    else
        work_dir="$(mktemp -d "$config_dir/.$name-keygen.XXXXXX")"
        if ! openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 \
                -out "$work_dir/private.pem" 2>/dev/null \
                || ! openssl pkcs8 -topk8 -nocrypt -in "$work_dir/private.pem" \
                    -outform DER 2>/dev/null | openssl base64 -A > "$work_dir/private.b64" \
                || ! openssl pkey -in "$work_dir/private.pem" -pubout -outform DER 2>/dev/null \
                    | openssl base64 -A > "$work_dir/public.b64"; then
            rm -f "$work_dir/private.pem" "$work_dir/private.b64" "$work_dir/public.b64"
            rmdir "$work_dir"
            printf 'Could not create the %s ES256 key pair.\n' "$name" >&2
            exit 1
        fi
        chmod 600 "$work_dir/private.b64" "$work_dir/public.b64"
        mv "$work_dir/private.b64" "$private_key_file"
        mv "$work_dir/public.b64" "$public_key_file"
        rm -f "$work_dir/private.pem"
        rmdir "$work_dir"
    fi

    work_dir="$(mktemp -d "$config_dir/.$name-validate.XXXXXX")"
    if ! openssl base64 -d -A -in "$private_key_file" -out "$work_dir/private.der" 2>/dev/null \
            || ! openssl base64 -d -A -in "$public_key_file" -out "$work_dir/public.der" 2>/dev/null \
            || ! openssl pkey -inform DER -in "$work_dir/private.der" \
                -pubout -outform DER -out "$work_dir/derived-public.der" 2>/dev/null \
            || ! openssl pkey -pubin -inform DER -in "$work_dir/public.der" -noout 2>/dev/null \
            || ! cmp -s "$work_dir/derived-public.der" "$work_dir/public.der"; then
        rm -f "$work_dir/private.der" "$work_dir/public.der" "$work_dir/derived-public.der"
        rmdir "$work_dir"
        printf 'The existing %s keys are invalid or do not form a matching pair.\n' "$name" >&2
        exit 1
    fi
    rm -f "$work_dir/private.der" "$work_dir/public.der" "$work_dir/derived-public.der"
    rmdir "$work_dir"
}

ensure_internal_pair "order-delivery-internal"
ensure_internal_pair "delivery-order-internal"

printf 'Local JWT secret and dedicated Order-to-Restaurant, Order-to-User, and Order-to-Delivery ES256 key pairs are ready in %s.\n' \
    "$config_dir"
