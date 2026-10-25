const MONEY_PATTERN = /^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/;

export function toMinorUnits(value: string): bigint | null {
  const normalized = value.trim();
  if (!MONEY_PATTERN.test(normalized)) return null;
  const [whole, fraction = ""] = normalized.split(".");
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
}

export function fromMinorUnits(value: bigint): string {
  const whole = value / 100n;
  const fraction = (value % 100n).toString().padStart(2, "0");
  return `${whole}.${fraction}`;
}

export function multiplyMoney(value: string, quantity: number): string {
  const minorUnits = toMinorUnits(value);
  if (minorUnits === null || !Number.isSafeInteger(quantity) || quantity < 0) return "0.00";
  return fromMinorUnits(minorUnits * BigInt(quantity));
}

export function sumMoney(values: string[]): string {
  const total = values.reduce((sum, value) => sum + (toMinorUnits(value) ?? 0n), 0n);
  return fromMinorUnits(total);
}

export function isPositiveMoney(value: string): boolean {
  return (toMinorUnits(value) ?? 0n) > 0n;
}

export function formatInr(value: string): string {
  const minorUnits = toMinorUnits(value);
  if (minorUnits === null) return "₹—";
  const rupees = minorUnits / 100n;
  const paise = (minorUnits % 100n).toString().padStart(2, "0");
  return `₹${new Intl.NumberFormat("en-IN").format(rupees)}.${paise}`;
}

export function formatLegacyMoney(value: string, currency: string): string {
  if (currency === "INR") return formatInr(value);
  if (currency === "USD") {
    const minorUnits = toMinorUnits(value);
    if (minorUnits === null) return "Legacy USD —";
    const dollars = minorUnits / 100n;
    const cents = (minorUnits % 100n).toString().padStart(2, "0");
    return `Legacy USD $${new Intl.NumberFormat("en-US").format(dollars)}.${cents}`;
  }
  return `Unsupported ${currency || "currency"} amount`;
}
