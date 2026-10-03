Trip: {tripSummary}

Days summary:
{summaryOfDays}

Write the following Markdown sections (in order). No JSON. No preamble.

## Overview
| Field | Value |
|---|---|
| From | {source} |
| To | {destination} |
| Dates | {startDate} → {endDate} |
| Duration | {totalDays} days |
| Travellers | {headcount} |
| Budget | {budgetPref} |

## Flights, Trains & Inter-city Transport
| Leg | Route | Date | Mode | Operator | Depart | Arrive | Duration | Cost (₹) | Book |
|---|---|---|---|---|---|---|---|---|---|
| 1 | [from → to] | [date] | [mode] | [airline or train name] | [time] | [time] | [Xh] | [amount] | [Book]([URL]) |
| 2 | [return] | [date] | [mode] | [airline or train name] | [time] | [time] | [Xh] | [amount] | [Book]([URL]) |

## Accommodation
| City | Hotel | Stars | Check-in | Check-out | Nights | Per Night (₹) | Total (₹) | Book |
|---|---|---|---|---|---|---|---|---|
| [city] | [name]([booking URL]) | [★★★] | [date] | [date] | [n] | [rate] | [total] | [Book]([URL]) |

## Cost Summary
| Category | Amount (₹) |
|---|---|
| Flights | [amount] |
| Hotels | [amount] |
| Food (all days) | [amount] |
| Activities (all days) | [amount] |
| Local Transport | [amount] |
| **Grand Total** | **[amount]** |
| Budget Remaining | [amount] |

## Travel Tips & Warnings
- [Visa / entry requirement tip]
- [Weather / packing tip]
- [Currency / payment tip]

## Summary & Closing Thoughts
[Provide a thoughtful travel summary and closure notes here. Mention local customs, culture, safety guidelines, and general recommendations for an excellent trip.]

CRITICAL FORMATTING INSTRUCTIONS:
1. You MUST generate ALL the sections and tables listed above. Do NOT omit any table.
2. Every table MUST contain all columns specified. If data is unknown or not set, use 'N/A' or estimated values.
3. In the Flights, Trains & Inter-city Transport table, include any inter-city rail or flight options as relevant.
4. Output ONLY valid Markdown. Do not enclose the output in code fences (e.g. ```markdown ... ```).

Output ONLY the Markdown. Start with '## Overview'.
