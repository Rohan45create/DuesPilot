import { loadEnv, getChatEndpoint } from './env.js';

export async function runS5(targetModelId, baseUrlOverride) {
  const { NEBIUS_API_KEY } = loadEnv();
  if (!NEBIUS_API_KEY) {
    throw new Error('NEBIUS_API_KEY is missing in .env');
  }

  const chatEndpoint = getChatEndpoint(baseUrlOverride);
  const model = targetModelId || 'nvidia/nemotron-nano-3-30b-a3b';

  const prompt =
    'Extract the invoice details as JSON with keys invoice_number and total_amount from: "Invoice #INV-5541 for Amount ₹95,000"';

  const testCases = [
    {
      name: 'prompt_only_no_response_format',
      format: undefined,
    },
    {
      name: 'json_object_mode',
      format: { type: 'json_object' },
    },
    {
      name: 'json_schema_mode',
      format: {
        type: 'json_schema',
        json_schema: {
          name: 'invoice_extraction',
          strict: true,
          schema: {
            type: 'object',
            properties: {
              invoice_number: { type: 'string' },
              total_amount: { type: 'number' },
            },
            required: ['invoice_number', 'total_amount'],
            additionalProperties: false,
          },
        },
      },
    },
  ];

  const results = [];

  for (const tc of testCases) {
    const startTime = Date.now();
    try {
      const payload = {
        model,
        messages: [{ role: 'user', content: prompt }],
        max_tokens: 1024,
        temperature: 0.1,
      };
      if (tc.format) {
        payload.response_format = tc.format;
      }

      const res = await fetch(chatEndpoint, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${NEBIUS_API_KEY}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      });

      const latency = Date.now() - startTime;
      const resText = await res.text();

      if (!res.ok) {
        results.push({
          mode: tc.name,
          status: res.status,
          latency,
          outcome: 'FAILED',
          response: resText.slice(0, 300),
        });
        continue;
      }

      const data = JSON.parse(resText);
      const rawContent = data.choices?.[0]?.message?.content || '';
      let parsed = null;
      let isValidJson = false;

      try {
        parsed = JSON.parse(rawContent.trim());
        isValidJson = true;
      } catch {
        isValidJson = false;
      }

      results.push({
        mode: tc.name,
        status: res.status,
        latency,
        outcome: 'SUCCESS',
        isValidJson,
        parsedSample: parsed,
        rawContentSnippet: rawContent.slice(0, 200).replace(/\r?\n/g, ' '),
        usage: data.usage || null,
      });
    } catch (err) {
      results.push({
        mode: tc.name,
        outcome: 'EXCEPTION',
        error: err.message,
      });
    }
  }

  return results;
}

if (process.argv[1]?.endsWith('s5_json_mode.js')) {
  runS5()
    .then((results) => console.log(JSON.stringify(results, null, 2)))
    .catch((err) => {
      console.error('Error running S-5:', err.message);
      process.exit(1);
    });
}
