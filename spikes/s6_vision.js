import { loadEnv, getChatEndpoint } from './env.js';

export async function runS6(candidateModelId, baseUrlOverride) {
  const { NEBIUS_API_KEY } = loadEnv();
  if (!NEBIUS_API_KEY) {
    throw new Error('NEBIUS_API_KEY is missing in .env');
  }

  const chatEndpoint = getChatEndpoint(baseUrlOverride);
  const modelId = candidateModelId || 'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning';
  const smallPngBase64 =
    'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';

  const startTime = Date.now();
  try {
    const res = await fetch(chatEndpoint, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${NEBIUS_API_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: modelId,
        messages: [
          {
            role: 'user',
            content: [
              { type: 'text', text: 'Describe what you see in this image in fewer than 10 words.' },
              { type: 'image_url', image_url: { url: smallPngBase64 } },
            ],
          },
        ],
        max_tokens: 128,
      }),
    });

    const latency = Date.now() - startTime;
    const resText = await res.text();

    return {
      testedModelId: modelId,
      status: res.status,
      latency,
      response: resText.slice(0, 400),
      isMultimodalSupported: res.ok,
    };
  } catch (err) {
    return {
      testedModelId: modelId,
      error: err.message,
      isMultimodalSupported: false,
    };
  }
}

if (process.argv[1]?.endsWith('s6_vision.js')) {
  runS6()
    .then((results) => console.log(JSON.stringify(results, null, 2)))
    .catch((err) => {
      console.error('Error running S-6:', err.message);
      process.exit(1);
    });
}
