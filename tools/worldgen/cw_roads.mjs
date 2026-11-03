// Runs the Leonida pipeline road engine (profiles, highway engineering, props)
// over community-world-v1 and dumps a compact JSON for the Minecraft converter.
// Usage: node cw_roads.mjs <pipeline/src> <community-world-v1 dir> <out.json>
import fs from 'node:fs';
import path from 'node:path';
const [,, pipe, cw, out] = process.argv;
const { readPipelineInput } = await import(path.resolve(pipe, 'lib/read-input.js'));
const { createProceduralRoads } = await import(path.resolve(pipe, 'explorer/src/procedural-roads.js'));
const env = path.join(path.resolve(cw), '..', 'cw-input.json');
fs.writeFileSync(env, JSON.stringify({ version: 1, seed: 1031, terrain: { kind: 'community-manifest', manifest: path.basename(path.resolve(cw)) + '/manifest.json' } }));
const input = readPipelineInput(env);
const t = input.terrain;
console.log('terrain keys', Object.keys(t));
const t0 = Date.now();
const r = createProceduralRoads({ network: input.roads, terrainHeight: t.heightAt, isWater: t.isWater, waterLevelAt: t.waterLevelAt, seed: 1031 });
console.log('roads built', (Date.now() - t0) / 1000, 's', JSON.stringify(r.stats.profiles).slice(0, 600));
const r2 = v => Math.round(v * 100) / 100;
const segs = r.profiles.segments.map(s => ({
  id: s.id, k: s.kind ?? s.k, fam: s.family, w: s.width, lanes: s.lanes, oneWay: s.oneWay, a: s.startNode, b: s.endNode,
  keys: Object.keys(s).join(','),
  pts: s.samples.map(p => [r2(p.x), r2(p.y), r2(p.z), r2(p.ground), p.bridge ? 1 : 0]),
}));
const nodes = r.profiles.nodes.map(n => ({ id: n.id, x: r2(n.x), y: r2(n.y), z: r2(n.z), segs: n.segmentIds, keys: Object.keys(n).join(',') }));
const lamps = (r.lampRecords || []).slice(0, 3);
const signals = [];
r.group.traverse(o => {
  if (o.isInstancedMesh && /signal/.test(o.name)) {
    const m = o.instanceMatrix.array;
    for (let i = 0; i < o.count; i++) signals.push([r2(m[i * 16 + 12]), r2(m[i * 16 + 13]), r2(m[i * 16 + 14]), r2(m[i * 16]), r2(m[i * 16 + 1]), r2(m[i * 16 + 2])]);
  }
});
const lampAll = (r.lampRecords || []).map(l => l);
fs.writeFileSync(out, JSON.stringify({ segs, nodes, lampSample: lamps, lamps: lampAll, signals }));
console.log('segments', segs.length, 'nodes', nodes.length, 'lamps', lampAll.length, 'signals', signals.length);
console.log('seg0', JSON.stringify(segs[0]).slice(0, 800));
console.log('lamp0', JSON.stringify(lamps));
