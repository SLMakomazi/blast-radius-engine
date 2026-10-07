import test from 'node:test';
import assert from 'node:assert/strict';
import { nodeView, graphFor, evidenceBuckets, matchesIncident, explainSignal, analysisOf } from './incident-visuals.js';
const event = (component, signal, family='HEALTH') => ({ component, signal, family, timestamp:'2026-10-06T12:00:00Z' });
const incident = { status:'ACTIVE', originComponent:'db', originConfidence:'HIGH', analysis:{ timeline:[], impacts:[] } };
test('absence of evidence never becomes healthy or confirmed failure', () => {
  for (const state of ['ORIGIN','OBSERVED','UNKNOWN']) assert.equal(nodeView({component:'db',state},incident).tone,'unknown');
  assert.equal(nodeView({component:'api',state:'THEORETICAL_ONLY'},incident).tone,'risk');
});
test('only hard liveness failure is red; readiness and HTTP 500 stay degraded', () => {
  assert.equal(nodeView({component:'db',state:'ORIGIN',evidence:[event('db','liveness health DOWN')]},incident).tone,'failed');
  assert.equal(nodeView({component:'api',state:'OBSERVED',evidence:[event('api','readiness health DOWN')]},incident).tone,'degraded');
  assert.equal(nodeView({component:'api',state:'OBSERVED',evidence:[event('api','aggregate health DOWN')]},incident).tone,'degraded');
  assert.equal(nodeView({component:'api',state:'OBSERVED',evidence:[event('api','application error HTTP 500','LOG')]},incident).tone,'degraded');
});
test('resolved incidents remain historical rather than asserting current health', () => {
  const view=nodeView({component:'db',state:'ORIGIN',evidence:[event('db','liveness health DOWN')]},{...incident,status:'RESOLVED'});
  assert.equal(view.tone,'recovered'); assert.equal(view.label,'Resolved snapshot'); assert.equal(view.resolved,true);
});
test('graph preserves branches and does not invent adjacency between siblings', () => {
  const impacts=[{component:'db',state:'ORIGIN',path:['db']},{component:'api',state:'OBSERVED',path:['db','api']},{component:'worker',state:'THEORETICAL_ONLY',path:['db','worker']},{component:'unknown',state:'UNEXPECTED',path:[]}];
  assert.deepEqual(graphFor({...incident,analysis:{impacts}}).edges,[{from:'db',to:'api'},{from:'db',to:'worker'}]);
});
test('dependency explanation names the immediate dependency and qualifies possible impact', () => {
  const view=nodeView({component:'web',state:'THEORETICAL_ONLY',path:['db','api','web']},incident);
  assert.match(view.relationship,/web depends on api/); assert.match(view.relationship,/not been observed/);
});
test('search includes impacted services and evidence, case-insensitively', () => {
  assert.ok(matchesIncident({...incident,analysis:{impacts:[{component:'Payments'}]}},'PAYMENTS'));
  assert.ok(matchesIncident({...incident,analysis:{timeline:[event('db','connection timeout')]}},'timeout'));
  assert.equal(matchesIncident(incident,'missing'),false);
});
test('timeline uses recorded timestamps and preserves family counts', () => {
  const timeline=[event('db','liveness health DOWN'),event('api','error','LOG'),{...event('api','slow','TRACE'),timestamp:'2026-10-06T12:01:00Z'},{timestamp:'invalid',family:'LOG'}];
  const buckets=evidenceBuckets({timeline});
  assert.equal(buckets.reduce((n,b)=>n+b.total,0),3); assert.equal(buckets[0].HEALTH,1); assert.equal(buckets.at(-1).TRACE,1);
  assert.deepEqual(evidenceBuckets({}),[]);
});
test('plain explanations do not invent a shutdown or exhausted memory', () => {
  assert.match(explainSignal('liveness health DOWN'),/unavailable or unreachable/);
  assert.match(explainSignal('readiness health DOWN'),/not ready/);
  assert.match(explainSignal('unrecognized event'),/does not identify/);
  assert.match(explainSignal('connection-pool contention'),/waiting for free database connections/);
});
test('supports stored JSON snapshots and deduplicates evidence', () => {
  assert.deepEqual(analysisOf({analysisSnapshot:'{"impacts":[]}'}),{impacts:[]});
  const e=event('db','liveness health DOWN');
  assert.equal(nodeView({component:'db',state:'ORIGIN',evidence:[e]},{...incident,analysis:{timeline:[e]}}).evidence.length,1);
});
