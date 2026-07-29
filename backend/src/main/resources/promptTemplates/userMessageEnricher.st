[CONTEXT FOR THIS MESSAGE]
conversationId: {conversationId}

USER MESSAGE:
{userMessage}

INSTRUCTION:
- Before replying, call getTripRequest(conversationId) to read the current trip state.
- If the user's message implies a change (e.g. source, destination, dates, adults, notes), call the updateTripRequest tool with the changed fields. Update the database using this tool.
- Never output raw JSON or structured data.