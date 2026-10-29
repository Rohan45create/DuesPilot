import { loadEnv, getChatEndpoint } from './env.js';

export async function runS2(models, baseUrlOverride) {
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

  const tokenBudgets = [256, 1024, 4096];
  const results = [];

  const prompt =
    "Extract the invoice details as JSON with fields {invoice_no, date, amount}: 'Tax Invoice INV-2024-998 dated 12/03/2024. Total amount payable: INR 45,000.'";

  for (const model of modelList) {
    for (const maxTokens of tokenBudgets) {
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
            messages: [{ role: 'user', content: prompt }],
            max_tokens: maxTokens,
            temperature: 0.1,
          }),
        });
        const latency = Date.now() - startTime;
        const resText = await res.text();

        if (!res.ok) {
          results.push({
            modelName: model.name,
            modelId: model.id,
            maxTokens,
            latency,
            status: res.status,
            error: resText,
          });
          continue;
        }

        const data = JSON.parse(resText);
        const choice = data.choices?.[0];
        const message = choice?.message || {};
        const content = message.content;
        const isContentEmpty = content === null || content === undefined || content.trim() === '';

        results.push({
          modelName: model.name,
          modelId: model.id,
          maxTokens,
          latency,
          status: res.status,
          finishReason: choice?.finish_reason,
          isContentEmpty,
          contentLength: content ? content.length : 0,
          contentSnippet: content ? content.slice(0, 150).replace(/\r?\n/g, ' ') : null,
          messageKeys: Object.keys(message),
          reasoningContentFound: 'reasoning_content' in message || 'reasoning' in message,
          usage: data.usage || null,
        });
      } catch (err) {
        results.push({
          modelName: model.name,
          modelId: model.id,
          maxTokens,
          latency: Date.now() - startTime,
          error: err.message,
        });
      }
    }
  }

  return results;
}

if (process.argv[1]?.endsWith('s2_tokens_and_reasoning.js')) {
  runS2()
    .then((results) => console.log(JSON.stringify(results, null, 2)))
    .catch((err) => {
      console.error('Error running S-2:', err.message);
      process.exit(1);
    });
}
