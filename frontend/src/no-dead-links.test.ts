import { describe, it, expect } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';

function getAllSourceFiles(dir: string, fileList: string[] = []): string[] {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);
    if (stat.isDirectory()) {
      getAllSourceFiles(filePath, fileList);
    } else if (file.endsWith('.tsx') || file.endsWith('.ts')) {
      fileList.push(filePath);
    }
  }
  return fileList;
}

describe('Frontend Interactive Element Code Quality', () => {
  const srcDir = path.resolve(__dirname, '.');
  const sourceFiles = getAllSourceFiles(srcDir).filter(
    (f) => !f.endsWith('.test.ts') && !f.endsWith('.test.tsx') && !f.includes('types')
  );

  it('contains zero href="#" dead links in any component or page', () => {
    const violations: { file: string; line: number; text: string }[] = [];

    for (const file of sourceFiles) {
      const content = fs.readFileSync(file, 'utf-8');
      const lines = content.split('\n');

      lines.forEach((line, index) => {
        // Match href="#" or href={'#'} or href={"#"}
        if (
          /href\s*=\s*["']#["']/.test(line) ||
          /href\s*=\s*\{["']#["']\}/.test(line)
        ) {
          violations.push({
            file: path.relative(srcDir, file),
            line: index + 1,
            text: line.trim(),
          });
        }
      });
    }

    expect(
      violations,
      `Found dead href="#" links:\n${violations
        .map((v) => `  ${v.file}:${v.line} -> ${v.text}`)
        .join('\n')}`
    ).toEqual([]);
  });

  it('contains zero empty onClick no-op handlers in any component or page', () => {
    const violations: { file: string; line: number; text: string }[] = [];

    for (const file of sourceFiles) {
      const content = fs.readFileSync(file, 'utf-8');
      const lines = content.split('\n');

      lines.forEach((line, index) => {
        // Match onClick={() => {}} or onClick={() => void 0} or onClick={() => null} or onClick={() => { /* ... */ }}
        if (
          /onClick\s*=\s*\{(\s*\(\)\s*=>\s*\{\s*\}|\s*\(\)\s*=>\s*undefined|\s*\(\)\s*=>\s*null|\s*\(\)\s*=>\s*\{\s*\/\*.*?\*\/\s*\})\}/.test(
            line
          )
        ) {
          violations.push({
            file: path.relative(srcDir, file),
            line: index + 1,
            text: line.trim(),
          });
        }
      });
    }

    expect(
      violations,
      `Found empty onClick no-op handlers:\n${violations
        .map((v) => `  ${v.file}:${v.line} -> ${v.text}`)
        .join('\n')}`
    ).toEqual([]);
  });
});
