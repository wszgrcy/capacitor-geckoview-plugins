import { dirname, resolve } from 'path';

import { parse } from 'yaml';

import { readFile, readJSON } from './fs.mjs';
import { root } from './repo.mjs';
import * as cp from './subprocess.mjs';

const stdio = 'inherit';
const cwd = root;

const runPnpm = async (args = []) => await cp.run('pnpm', args, { cwd, stdio });

/**
 * 列出所有 workspace 包（非 private）。
 * 用 yaml 解析 pnpm-workspace.yaml 的 packages 字段（即各文件夹名），
 * 直接读取各文件夹下的 package.json。
 */
export const ls = async () => {
  const yaml = await readFile(resolve(root, 'pnpm-workspace.yaml'), 'utf8');
  const workspace = parse(yaml);
  const patterns = workspace.packages ?? [];

  const packages = [];

  for (const pattern of patterns) {
    const dir = resolve(root, pattern);
    const pkg = await readJSON(resolve(dir, 'package.json'));

    if (pkg.private) {
      continue;
    }

    packages.push({
      name: pkg.name,
      version: pkg.version,
      private: pkg.private ?? false,
      location: dir,
    });
  }

  return packages;
};

export const exec = async (args = []) => await runPnpm(['-r', 'exec', ...args]);
