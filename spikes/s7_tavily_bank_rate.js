import { loadEnv } from './env.js';

export async function runS7() {
  const { TAVILY_API_KEY } = loadEnv();
  if (!TAVILY_API_KEY) {
    throw new Error('TAVILY_API_KEY is missing in .env');
  }

  const queries = [
    {
      id: 'Q1',
      query: 'current Bank Rate Reserve Bank of India',
      include_domains: ['rbi.org.in'],
    },
    {
      id: 'Q2',
      query: 'Reserve Bank of India Bank Rate policy rates table',
      include_domains: ['rbi.org.in'],
    },
    {
      id: 'Q3',
      query: 'Bank Rate percent effective date site:rbi.org.in',
      include_domains: ['rbi.org.in'],
    },
  ];

  const results = [];

  // Regex patterns to detect Bank Rate percentage and dates in RBI content
  const rateRegex = /Bank\s+Rate(?:\s*[:\-–—]|\s+is|\s+at|\s+stands\s+at)?\s*(\d{1,2}(?:\.\d{1,2})?)\s*(?:%|percent)/i;
  const dateRegex = /(?:effective|with\s+effect\s+from|w\.e\.f\.?|dated?|as\s+on)\s*([A-Za-z]+\s+\d{1,2},\s*\d{4}|\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}|\d{1,2}\s+[A-Za-z]+\s+\d{4})/i;

  for (const q of queries) {
    const startTime = Date.now();
    try {
      const res = await fetch('https://api.tavily.com/search', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          api_key: TAVILY_API_KEY,
          query: q.query,
          include_domains: q.include_domains,
          search_depth: 'advanced',
          max_results: 5,
        }),
      });

      const latency = Date.now() - startTime;
      const resText = await res.text();

      if (!res.ok) {
        results.push({
          id: q.id,
          query: q.query,
          status: res.status,
          latency,
          error: resText.slice(0, 300),
          canParseValue: false,
          canParseDate: false,
        });
        continue;
      }

      const data = JSON.parse(resText);
      const searchResults = (data.results || []).map((r) => {
        const textToSearch = `${r.title || ''} ${r.content || ''}`;
        const rateMatch = textToSearch.match(rateRegex);
        const dateMatch = textToSearch.match(dateRegex);

        return {
          title: r.title,
          url: r.url,
          score: r.score,
          snippet: (r.content || '').slice(0, 250),
          extractedRate: rateMatch ? `${rateMatch[1]}%` : null,
          extractedDate: dateMatch ? dateMatch[1] : null,
        };
      });

      const foundRate = searchResults.find((r) => r.extractedRate !== null);
      const foundDate = searchResults.find((r) => r.extractedDate !== null);

      results.push({
        id: q.id,
        query: q.query,
        status: res.status,
        latency,
        resultCount: searchResults.length,
        canParseValue: Boolean(foundRate),
        parsedRateSample: foundRate?.extractedRate || null,
        canParseDate: Boolean(foundDate),
        parsedDateSample: foundDate?.extractedDate || null,
        topSources: searchResults.slice(0, 3).map((s) => ({
          title: s.title,
          url: s.url,
          extractedRate: s.extractedRate,
          extractedDate: s.extractedDate,
        })),
      });
    } catch (err) {
      results.push({
        id: q.id,
        query: q.query,
        error: err.message,
        canParseValue: false,
        canParseDate: false,
      });
    }
  }

  return results;
}

if (process.argv[1]?.endsWith('s7_tavily_bank_rate.js')) {
  runS7()
    .then((results) => console.log(JSON.stringify(results, null, 2)))
    .catch((err) => {
      console.error('Error running S-7:', err.message);
      process.exit(1);
    });
}
