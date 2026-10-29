import { loadEnv, getChatEndpoint } from './env.js';

export async function runS4(models, baseUrlOverride) {
  const { NEBIUS_API_KEY } = loadEnv();
  if (!NEBIUS_API_KEY) {
    throw new Error('NEBIUS_API_KEY is missing in .env');
  }

  const chatEndpoint = getChatEndpoint(baseUrlOverride);

  const modelList = models || [
    { name: 'Nano', id: 'nvidia/nemotron-nano-3-30b-a3b' },
    { name: 'Super', id: 'nvidia/nemotron-3-super-120b-a12b' },
    { name: 'Ultra', id: 'nvidia/nemotron-3-ultra-550b-a55b' },
  ];

  // Construct ~30,000 tokens (~120,000 characters)
  const lineTemplate = (i) =>
    `Entry #${i}: Invoice INV-${String(i).padStart(5, '0')} dated 2024-01-15 issued to Buyer-${i % 20} for items category MSME-General amount ₹${(i * 137) % 500000}. Status: Pending reconciliation.\n`;

  let longText = 'Here is a ledger transcript:\n';
  let i = 1;
  while (longText.length < 120000) {
    longText += lineTemplate(i++);
  }
  longText += '\nQuestion: Is entry #42 recorded above? Answer YES or NO.';

  const results = [];

  for (const model of modelList) {
    const startTime = Date.now();
    try {
      const res = await fetch(chatEndpoint, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${NEBIUS_API_KEY}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          model: model.id,
          messages: [{ role: 'user', content: longText }],
          max_tokens: 64,
          temperature: 0.1,
        }),
      });

      const latency = Date.now() - startTime;
      const resText = await res.text();

      if (!res.ok) {
        results.push({
          modelName: model.name,
          modelId: model.id,
          status: res.status,
          latency,
          outcome: 'ERROR_OR_EXCEEDED',
          response: resText.slice(0, 400),
        });
      } else {
        const data = JSON.parse(resText);
        results.push({
          modelName: model.name,
          modelId: model.id,
          status: res.status,
          latency,
          outcome: 'SUCCESS',
          promptTokens: data.usage?.prompt_tokens,
          completionTokens: data.usage?.completion_tokens,
          totalTokens: data.usage?.total_tokens,
          reply: data.choices?.[0]?.message?.content?.slice(0, 100),
        });
      }
    } catch (err) {
      results.push({
        modelName: model.name,
        modelId: model.id,
        outcome: 'EXCEPTION',
        error: err.message,
      });
    }
  }

  return results;
}

if (process.argv[1]?.endsWith('s4_context_limits.js')) {
  runS4()
    .then((results) => console.log(JSON.stringify(results, null, 2)))
    .catch((err) => {
      console.error('Error running S-4:', err.message);
      process.exit(1);
    });
}
