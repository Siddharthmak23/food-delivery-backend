import { describe, expect, it } from "vitest";
import {
  formatInr,
  formatLegacyMoney,
  fromMinorUnits,
  isPositiveMoney,
  multiplyMoney,
  sumMoney,
  toMinorUnits,
} from "./money";

describe("INR money helpers", () => {
  it("uses integer paise for totals and formatting", () => {
    expect(toMinorUnits("250.05")).toBe(25005n);
    expect(multiplyMoney("12.50", 3)).toBe("37.50");
    expect(sumMoney(["12.50", "37.50", "0.05"])).toBe("50.05");
    expect(formatInr("250.00")).toBe("₹250.00");
    expect(fromMinorUnits(10_000_005n)).toBe("100000.05");
    expect(isPositiveMoney("0.01")).toBe(true);
    expect(isPositiveMoney("0.00")).toBe(false);
    expect(formatLegacyMoney("3.99", "USD")).toBe("Legacy USD $3.99");
  });

  it("does not interpret malformed or negative values as valid money", () => {
    expect(toMinorUnits("12.999")).toBeNull();
    expect(toMinorUnits("-1.00")).toBeNull();
    expect(formatInr("12.999")).toBe("₹—");
  });
});
