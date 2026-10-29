import { loadEnv, getChatEndpoint } from './env.js';

export async function runS3(targetModelId, baseUrlOverride) {
  const { NEBIUS_API_KEY } = loadEnv();
  if (!NEBIUS_API_KEY) {
    throw new Error('NEBIUS_API_KEY is missing in .env');
  }

  const chatEndpoint = getChatEndpoint(baseUrlOverride);
  const model = targetModelId || 'nvidia/nemotron-nano-3-30b-a3b';

  const testCases = [
    {
      name: 'baseline_no_flags',
      body: {},
    },
    {
      name: 'chat_template_kwargs_enable_thinking_false',
      body: {
        chat_template_kwargs: { enable_thinking: false },
      },
    },
    {
      name: 'chat_template_kwargs_enable_thinking_true',
      body: {
        chat_template_kwargs: { enable_thinking: true },
      },
    },
    {
      name: 'reasoning_effort_none',
      body: {
        reasoning_effort: 'none',
      },
    },
    {
      name: 'reasoning_effort_low',
      body: {
        reasoning_effort: 'low',
      },
    },
    {
      name: 'extra_body_enable_thinking_false',
      body: {
        extra_body: { enable_thinking: false },
      },
    },
    {
      name: 'thinking_type_disabled',
      body: {
        thinking: { type: 'disabled' },
      },
    },
  ];

  const results = [];
  const prompt = 'What is 15 + 27? Reply with only the number.';

  for (const tc of testCases) {
    const startTime = Date.now();
    try {
      const payload = {
        model,
        messages: [{ role: 'user', content: prompt }],
        max_tokens: 512,
        temperature: 0.1,
        ...tc.body,
      };

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
          testCase: tc.name,
          status: res.status,
          latency,
          outcome: 'REJECTED_OR_ERROR',
          response: resText.slice(0, 300),
        });
        continue;
      }

      const data = JSON.parse(resText);
      const choice = data.choices?.[0];
      const message = choice?.message || {};

      results.push({
        testCase: tc.name,
        status: res.status,
        latency,
        outcome: 'ACCEPTED',
        contentSnippet: message.content ? message.content.slice(0, 100).replace(/\r?\n/g, ' ') : null,
        usage: data.usage || null,
        reasoningTokens:
          data.usage?.completion_tokens_details?.reasoning_tokens ??
          data.usage?.reasoning_tokens ??
          null,
      });
    } catch (err) {
      results.push({
        testCase: tc.name,
        outcome: 'EXCEPTION',
        error: err.message,
      });
    }
  }

  return results;
}

if (process.argv[1]?.endsWith('s3_reasoning_control.js')) {
  runS3()
    .then((results) => console.log(JSON.stringify(results, null, 2)))
    .catch((err) => {
      console.error('Error running S-3:', err.message);
      process.exit(1);
    });
}
