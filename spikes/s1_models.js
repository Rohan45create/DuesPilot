import { loadEnv } from './env.js';

export async function runS1() {
  const { NEBIUS_API_KEY } = loadEnv();
  if (!NEBIUS_API_KEY) {
    throw new Error('NEBIUS_API_KEY is missing in .env');
  }

  const primaryUrl = 'https://api.tokenfactory.nebius.com/v1/models';
  const nvidiaUrl = 'https://integrate.api.nvidia.com/v1/models';

  // 1. Probe primary endpoint requested: https://api.tokenfactory.nebius.com/v1/models
  const t0 = Date.now();
  let primaryRes;
  let primaryError = null;
  let primaryStatus = null;
  try {
    primaryRes = await fetch(primaryUrl, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${NEBIUS_API_KEY}`,
        'Content-Type': 'application/json',
      },
    });
    primaryStatus = primaryRes.status;
    if (!primaryRes.ok) {
      primaryError = await primaryRes.text();
    }
  } catch (err) {
    primaryError = err.message;
  }
  const primaryLatency = Date.now() - t0;

  let activeModels = [];
  let activeEndpoint = null;
  let nvidiaStatus = null;
  let nvidiaLatency = null;

  if (primaryRes && primaryRes.ok) {
    const data = await primaryRes.json();
    activeModels = data.data || [];
    activeEndpoint = primaryUrl;
  } else {
    // 2. Probe NVIDIA API Catalog endpoint (matches nvapi-* keys)
    const t1 = Date.now();
    try {
      const nRes = await fetch(nvidiaUrl, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${NEBIUS_API_KEY}`,
          'Content-Type': 'application/json',
        },
      });
      nvidiaStatus = nRes.status;
      nvidiaLatency = Date.now() - t1;
      if (nRes.ok) {
        const nData = await nRes.json();
        activeModels = nData.data || [];
        activeEndpoint = nvidiaUrl;
      }
    } catch (err) {
      // ignore
    }
  }

  const nemotronModels = activeModels
    .filter((m) => m.id.toLowerCase().includes('nemotron'))
    .map((m) => ({
      id: m.id,
      owned_by: m.owned_by,
      created: m.created,
    }));

  return {
    primaryEndpoint: {
      url: primaryUrl,
      status: primaryStatus,
      latency: primaryLatency,
      error: primaryError,
    },
    activeEndpoint,
    nvidiaEndpoint: {
      url: nvidiaUrl,
      status: nvidiaStatus,
      latency: nvidiaLatency,
    },
    totalModels: activeModels.length,
    nemotronModels,
  };
}

if (process.argv[1]?.endsWith('s1_models.js')) {
  runS1()
    .then((result) => console.log(JSON.stringify(result, null, 2)))
    .catch((err) => {
      console.error('Error running S-1:', err.message);
      process.exit(1);
    });
}
