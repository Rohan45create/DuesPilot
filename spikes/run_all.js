import fs from 'fs';
import path from 'path';
import { runS1 } from './s1_models.js';
import { runS2 } from './s2_tokens_and_reasoning.js';
import { runS3 } from './s3_reasoning_control.js';
import { runS4 } from './s4_context_limits.js';
import { runS5 } from './s5_json_mode.js';
import { runS6 } from './s6_vision.js';
import { runS7 } from './s7_tavily_bank_rate.js';

export async function runAllSpikes() {
  const reportPath = path.resolve(process.cwd(), 'docs', 'spike-report.md');
  const report = [];

  report.push('# DuesPilot — Spikes Execution Report (P0)');
  report.push(`**Executed on:** ${new Date().toISOString()}`);
  report.push('**Environment:** Node ' + process.version + ' (Zero dependencies)\n');

  console.log('=== Running Spike S-1: Models listing ===');
  let s1Result;
  let nanoModel = 'nvidia/nemotron-nano-3-30b-a3b';
  let superModel = 'nvidia/nemotron-3-super-120b-a12b';
  let ultraModel = 'nvidia/nemotron-3-ultra-550b-a55b';
  let omniModel = 'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning';
  let activeBaseUrl = null;

  try {
    s1Result = await runS1();
    report.push('## S-1: Models Listing & Exact IDs');
    report.push(`- **Requested Primary Endpoint (\`https://api.tokenfactory.nebius.com/v1/models\`):** HTTP ${s1Result.primaryEndpoint.status} (${s1Result.primaryEndpoint.latency}ms)`);
    if (s1Result.primaryEndpoint.error) {
      report.push(`  - Raw Response: \`${s1Result.primaryEndpoint.error.trim()}\``);
      report.push(`  - Finding: Key with \`nvapi-\` prefix is an NVIDIA API Catalog key (from build.nvidia.com) rather than a Nebius Token Factory console key, resulting in 401 Unauthorized at api.tokenfactory.nebius.com.`);
    }

    if (s1Result.nvidiaEndpoint.status) {
      report.push(`- **NVIDIA Catalog Endpoint (\`https://integrate.api.nvidia.com/v1/models\`):** HTTP ${s1Result.nvidiaEndpoint.status} (${s1Result.nvidiaEndpoint.latency}ms)`);
    }

    report.push(`- **Active In-Use Endpoint:** \`${s1Result.activeEndpoint || 'None'}\``);
    report.push(`- **Total Models Available:** ${s1Result.totalModels}`);
    report.push(`- **Nemotron Family Models Found in Registry:** ${s1Result.nemotronModels.length}`);

    if (s1Result.nemotronModels && s1Result.nemotronModels.length > 0) {
      report.push('\n| Model ID | Owned By | Created |');
      report.push('|---|---|---|');
      for (const m of s1Result.nemotronModels) {
        report.push(`| \`${m.id}\` | ${m.owned_by || 'N/A'} | ${m.created ? new Date(m.created * 1000).toISOString() : 'N/A'} |`);
      }

      // Exact model bindings
      const nanoMatch = s1Result.nemotronModels.find(
        (m) => m.id.includes('nemotron-nano-3-30b-a3b') || m.id.includes('Nano-30B')
      );
      if (nanoMatch) nanoModel = nanoMatch.id;

      const superMatch = s1Result.nemotronModels.find(
        (m) => m.id.includes('nemotron-3-super-120b-a12b')
      );
      if (superMatch) superModel = superMatch.id;

      const ultraMatch = s1Result.nemotronModels.find(
        (m) => m.id.includes('nemotron-3-ultra-550b-a55b')
      );
      if (ultraMatch) ultraModel = ultraMatch.id;

      const omniMatch = s1Result.nemotronModels.find(
        (m) => m.id.includes('nemotron-3-nano-omni-30b-a3b-reasoning')
      );
      if (omniMatch) omniModel = omniMatch.id;
    }

    if (s1Result.activeEndpoint) {
      activeBaseUrl = s1Result.activeEndpoint.replace(/\/models$/, '');
    }
  } catch (err) {
    report.push(`\n**Error in S-1:** ${err.message}`);
  }

  const resolvedModels = [
    { name: 'Nano', id: nanoModel },
    { name: 'Super', id: superModel },
    { name: 'Ultra', id: ultraModel },
  ];

  console.log('\n=== Running Spike S-2: Tokens & Reasoning Analysis ===');
  try {
    const s2Result = await runS2(resolvedModels, activeBaseUrl);
    report.push('\n---\n## S-2: Empty Content & Reasoning Tokens Analysis (Nano, Super, Ultra)');
    report.push('| Model | Max Tokens | Status | Latency | Finish Reason | Content Empty? | Content Preview | Usage (Prompt / Compl / Total) | Reasoning Tokens |');
    report.push('|---|---|---|---|---|---|---|---|---|');

    for (const r of s2Result) {
      if (r.error || r.status !== 200) {
        report.push(`| ${r.modelName} (\`${r.modelId}\`) | ${r.maxTokens} | ${r.status || 'ERR'} | ${r.latency}ms | - | ERROR | \`${(r.error || r.response || 'Not available for account').slice(0, 50)}\` | - | - |`);
      } else {
        const u = r.usage || {};
        const reasoning = u.completion_tokens_details?.reasoning_tokens ?? u.reasoning_tokens ?? 'N/A';
        report.push(
          `| ${r.modelName} (\`${r.modelId}\`) | ${r.maxTokens} | ${r.status} | ${r.latency}ms | ${r.finishReason || 'N/A'} | **${r.isContentEmpty ? 'YES (EMPTY)' : 'NO'}** | \`${r.contentSnippet ? r.contentSnippet.slice(0, 80) : 'NONE'}\` | ${u.prompt_tokens || 0} / ${u.completion_tokens || 0} / ${u.total_tokens || 0} | ${reasoning} |`
        );
      }
    }
  } catch (err) {
    report.push(`\n**Error in S-2:** ${err.message}`);
  }

  console.log('\n=== Running Spike S-3: Reasoning Controls ===');
  try {
    const s3Result = await runS3(ultraModel, activeBaseUrl);
    report.push('\n---\n## S-3: Reasoning Controls');
    report.push(`*Tested against active model: \`${ultraModel}\`*`);
    report.push('\n| Parameter Configuration | Outcome | HTTP Status | Latency | Completion Tokens | Details / Content Snippet |');
    report.push('|---|---|---|---|---|---|');

    for (const r of s3Result) {
      report.push(
        `| \`${r.testCase}\` | **${r.outcome}** | ${r.status || 'ERR'} | ${r.latency || 0}ms | ${r.usage?.completion_tokens ?? 'N/A'} | ${r.response ? r.response.slice(0, 100) : (r.contentSnippet || 'OK')} |`
      );
    }
  } catch (err) {
    report.push(`\n**Error in S-3:** ${err.message}`);
  }

  console.log('\n=== Running Spike S-4: Context Limits (~30k tokens) ===');
  try {
    const s4Result = await runS4(resolvedModels, activeBaseUrl);
    report.push('\n---\n## S-4: Context Window Limits (~30k Tokens Prompt)');
    report.push('| Model | HTTP Status | Outcome | Latency | Prompt Tokens Recorded | Details / Snippet |');
    report.push('|---|---|---|---|---|---|');

    for (const r of s4Result) {
      report.push(
        `| ${r.modelName} (\`${r.modelId}\`) | ${r.status || 'ERR'} | **${r.outcome}** | ${r.latency || 0}ms | ${r.promptTokens || 'N/A'} | ${r.response ? r.response.slice(0, 150) : (r.reply || 'OK')} |`
      );
    }
  } catch (err) {
    report.push(`\n**Error in S-4:** ${err.message}`);
  }

  console.log('\n=== Running Spike S-5: JSON Modes ===');
  try {
    const s5Result = await runS5(ultraModel, activeBaseUrl);
    report.push('\n---\n## S-5: Response Format JSON Modes');
    report.push(`*Tested against active model: \`${ultraModel}\`*`);
    report.push('\n| Mode | Status | Outcome | Is Valid JSON? | Parsed Output / Error |');
    report.push('|---|---|---|---|---|');

    for (const r of s5Result) {
      report.push(
        `| \`${r.mode}\` | ${r.status || 'ERR'} | **${r.outcome}** | ${r.isValidJson ? 'YES' : 'NO'} | ${r.parsedSample ? JSON.stringify(r.parsedSample) : (r.response || r.error || 'N/A')} |`
      );
    }
  } catch (err) {
    report.push(`\n**Error in S-5:** ${err.message}`);
  }

  console.log('\n=== Running Spike S-6: Vision / Multimodal ===');
  try {
    report.push('\n---\n## S-6: Vision / Omni Multimodal Support');
    report.push(`- **Vision Model Tested:** \`${omniModel}\``);
    const s6Result = await runS6(omniModel, activeBaseUrl);
    report.push(`- **HTTP Status:** ${s6Result.status || 'ERR'}`);
    report.push(`- **Latency:** ${s6Result.latency || 0}ms`);
    report.push(`- **Multimodal Image Input Accepted:** **${s6Result.isMultimodalSupported ? 'YES' : 'NO'}**`);
    report.push(`- **Raw Response:** \`${s6Result.response || s6Result.error}\``);
  } catch (err) {
    report.push(`\n**Error in S-6:** ${err.message}`);
  }

  console.log('\n=== Running Spike S-7: Tavily RBI Bank Rate ===');
  try {
    const s7Result = await runS7();
    report.push('\n---\n## S-7: Tavily RBI Bank Rate Retrieval');
    report.push('| Query ID | Search Query | HTTP Status | Result Count | Value Parsed? | Sample Rate | Date Parsed? | Sample Date |');
    report.push('|---|---|---|---|---|---|---|---|');

    for (const r of s7Result) {
      report.push(
        `| ${r.id} | "${r.query}" | ${r.status || 'ERR'} | ${r.resultCount || 0} | **${r.canParseValue ? 'YES' : 'NO'}** | ${r.parsedRateSample || 'N/A'} | **${r.canParseDate ? 'YES' : 'NO'}** | ${r.parsedDateSample || 'N/A'} |`
      );
    }

    report.push('\n### Citations & Top Sources from Tavily:');
    for (const r of s7Result) {
      if (r.topSources && r.topSources.length > 0) {
        report.push(`\n**${r.id} ("${r.query}"):**`);
        for (const s of r.topSources) {
          report.push(`- [${s.title}](${s.url}) — Extracted Date: ${s.extractedDate || 'None'}`);
        }
      }
    }
  } catch (err) {
    report.push(`\n**Error in S-7:** ${err.message}`);
  }

  fs.writeFileSync(reportPath, report.join('\n') + '\n', 'utf8');
  console.log(`\nAll spikes complete. Written findings to ${reportPath}`);
}

if (process.argv[1]?.endsWith('run_all.js')) {
  runAllSpikes().catch((err) => {
    console.error('Fatal error running spikes:', err.message);
    process.exit(1);
  });
}
