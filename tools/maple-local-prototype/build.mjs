import { build } from 'esbuild';
await build({
  entryPoints: ['src/core.ts'], outfile: 'dist/core.mjs', bundle: true,
  platform: 'node', target: 'node22', format: 'esm',
  alias: { '@maple/shared': './vendor/maple-auction/shared.ts' },
});
console.log('Built local auction adapter and comparison model.');
