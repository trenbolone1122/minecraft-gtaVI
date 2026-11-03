// Samples the pipeline community terrain provider (ground height, water, water level)
// on a regular grid and writes raw rasters for the Minecraft converter.
// Usage: node cw_terrain.mjs <pipeline/src> <community-world-v1 dir> <outdir> <stepM> [rowsLimit]
import fs from 'node:fs';
import path from 'node:path';
const [,, pipe, cw, outdir, stepArg, lim] = process.argv;
const step = Number(stepArg);
const { readPipelineInput } = await import(path.resolve(pipe, 'lib/read-input.js'));
const env = path.join(path.resolve(cw), '..', 'cw-input.json');
fs.writeFileSync(env, JSON.stringify({ version: 1, seed: 1031, terrain: { kind: 'community-manifest', manifest: path.basename(path.resolve(cw)) + '/manifest.json' } }));
const t = readPipelineInput(env).terrain;
const W = 16384 / step, H = Math.min(20480 / step, lim ? Number(lim) : 1e9);
const ground = new Int16Array(W * H), level = new Int16Array(W * H), water = new Uint8Array(W * H);
const t0 = Date.now();
for (let j = 0; j < H; j++) {
  const y = 11264 - (j + 0.5) * step;
  for (let i = 0; i < W; i++) {
    const x = -11264 + (i + 0.5) * step, k = j * W + i;
    ground[k] = Math.round(t.groundHeightAt(x, y) * 10);
    const w = t.isWater(x, y);
    water[k] = w ? 1 : 0;
    if (w) level[k] = Math.round(t.waterLevelAt(x, y) * 10);
  }
  if (j % 500 === 0) console.log('row', j, (Date.now() - t0) / 1000);
}
console.log('done', W, H, (Date.now() - t0) / 1000, 's');
fs.writeFileSync(path.join(outdir, `ground-${step}.i16`), Buffer.from(ground.buffer));
fs.writeFileSync(path.join(outdir, `level-${step}.i16`), Buffer.from(level.buffer));
fs.writeFileSync(path.join(outdir, `water-${step}.u8`), Buffer.from(water.buffer));
console.log('sample h at Ocean Dr', t.heightAt(2038, 970), t.groundHeightAt(2038, 970), t.rawHeightAt(2038, 970), t.isWater(2100, 970), t.waterLevelAt(2300, 970));
