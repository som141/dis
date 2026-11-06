import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { loadCharacterSnapshot } from '../dist/core.mjs';
import { requireObject } from '../src/domain.mjs';

test('a stalled Nexon response ends the equipment request with a specific error', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'maple-nexon-timeout-'));
  const originalTimeout = AbortSignal.timeout;
  const originalFetch = globalThis.fetch;
  const originalKey = process.env.NEXON_DEVELOPER_KEY;
  const originalDataDir = process.env.MAPLE_AUCTION_DATA_DIR;
  try {
    process.env.NEXON_DEVELOPER_KEY = 'fixture-service-key';
    process.env.MAPLE_AUCTION_DATA_DIR = directory;
    AbortSignal.timeout = () => {
      const controller = new AbortController();
      setTimeout(() => controller.abort(new DOMException('deadline', 'TimeoutError')), 25);
      return controller.signal;
    };
    globalThis.fetch = (_url, { signal }) => new Promise((_resolve, reject) => {
      signal.addEventListener('abort', () => reject(signal.reason), { once:true });
    });
    await assert.rejects(loadCharacterSnapshot('deadline-fixture'), /넥슨 오픈 API 응답 시간 초과 \/id/);
    assert.throws(() => requireObject('넥슨 오픈 API 캐릭터 조회 실패: 넥슨 오픈 API 응답 시간 초과 /id'), error => {
      assert.equal(error.code, 'NEXON_TIMEOUT');
      return true;
    });
  } finally {
    AbortSignal.timeout = originalTimeout;
    globalThis.fetch = originalFetch;
    if (originalKey === undefined) delete process.env.NEXON_DEVELOPER_KEY;
    else process.env.NEXON_DEVELOPER_KEY = originalKey;
    if (originalDataDir === undefined) delete process.env.MAPLE_AUCTION_DATA_DIR;
    else process.env.MAPLE_AUCTION_DATA_DIR = originalDataDir;
    await rm(directory, { recursive:true, force:true });
  }
});
