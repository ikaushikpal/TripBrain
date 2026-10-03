Trip: {tripSummary}

Reference data (use for inspiration, do NOT quote verbatim):
{destCtx}

{prevCtx}

Write ONLY the Markdown itinerary section for Day {currentDay} of {totalDays} ({dayDate}).

Use this exact structure:
### Day {currentDay} — [City Name] ({dayDate})
**Weather:** [condition, temp range, rain chance]

**Activities:**
- [Activity name]([Google Maps URL]) — [duration], ₹[cost] — [1-line note]
- [Activity name]([Google Maps URL]) — [duration], ₹[cost] — [1-line note]

**Meals:**
- Breakfast: [Restaurant name] — [cuisine] — ₹[cost]
- Lunch: [Restaurant name] — [cuisine] — ₹[cost]
- Dinner: [Restaurant name] — [cuisine] — ₹[cost]

**Local Transport:** [from → to, mode, ₹cost]

| Category | Cost (₹) |
|---|---|
| Activities | [amount] |
| Food | [amount] |
| Transport | [amount] |
| **Day Total** | **[amount]** |

Output ONLY the Markdown above. No JSON. No preamble. Start with '###'.
