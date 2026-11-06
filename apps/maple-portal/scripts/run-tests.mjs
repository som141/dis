import { readdir } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { join } from 'node:path';

const files = (await readdir('test')).filter(name => name.endsWith('.test.mjs')).sort().map(name => join('test', name));
if (files.length === 0) throw new Error('No test files found');

const child = spawn(process.execPath, ['--test', ...files], { stdio:'inherit' });
child.once('error', error => { console.error(error); process.exitCode = 1; });
child.once('exit', (code, signal) => { process.exitCode = signal ? 1 : code ?? 1; });
