<context>
  <conversation_id>{conversationId}</conversation_id>
</context>

<user_message>
{userMessage}
</user_message>

<execution_instructions>
  <instruction priority="HIGH">Always call getTripRequest(conversationId) first to inspect current trip parameters.</instruction>
  <instruction priority="CRITICAL">Whenever this user message provides or implies ANY new or updated travel details (e.g. source, destination, travel dates, duration, adults, children, traveler type, budget, currency, budget preference, hotel stars, cabin class, must-visit places, avoid places, or special notes), you MUST IMMEDIATELY call updateTripRequest to save these fields into the database.</instruction>
  <instruction>When specific attractions, sights, or cities to visit are mentioned, include them in mustVisitPlaces so they populate as markers on the route map.</instruction>
  <instruction>Never output raw JSON or internal tool details in your final response.</instruction>
</execution_instructions>