import { readFileSync, writeFileSync } from 'fs';
import { dirname, resolve } from 'path';
import { fileURLToPath } from 'url';
import { parse } from 'yaml';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const { $ } = await import('execa');

// 从根 package.json 读取统一版本号
const { version } = JSON.parse(readFileSync(resolve(root, 'package.json'), 'utf8'));

if (!version) {
  throw new Error('根 package.json 缺少 version');
}

const TAG = process.env['PUBLISH_TAG'] ?? 'latest';

// 统一 tag 已存在则跳过
const result = await $({ reject: false })`git ls-remote --tags --exit-code origin refs/tags/${version}`;

if (result.stdout) {
  console.log(`⏭️ 跳过，tag ${version} 已存在`);
  process.exit(0);
}

// 列出非 private 的 workspace 包
const yaml = readFileSync(resolve(root, 'pnpm-workspace.yaml'), 'utf8');
const { packages } = parse(yaml);

const pkgs = packages
  .map((dir) => {
    const location = resolve(root, dir);
    const pkgPath = resolve(location, 'package.json');
    const pkg = JSON.parse(readFileSync(pkgPath, 'utf8'));

    return { name: pkg.name, location, pkgPath, private: pkg.private };
  })
  .filter((p) => !p.private);

// 更新子包版本为根版本号并发布
for (const pkg of pkgs) {
  const pkgJson = JSON.parse(readFileSync(pkg.pkgPath, 'utf8'));
  pkgJson.version = version;
  writeFileSync(pkg.pkgPath, JSON.stringify(pkgJson, null, 2) + '\n');
  console.log(`📝 ${pkg.name} -> ${version}`);

  await $({ stdio: 'inherit' })('npm', [
    'publish',
    '--access=public',
    '--registry=https://registry.npmjs.org',
    pkg.location,
    '--tag',
    TAG,
    '--provenance',
  ]);
  console.log(`⬆️${pkg.name}✅`);
}

// 生成 changelog、提交、打统一 tag
await $({ stdio: 'inherit' })`conventional-changelog -p angular -i CHANGELOG.md -s`;
await $({ stdio: 'inherit' })`git add CHANGELOG.md`;
await $({ stdio: 'inherit' })`git commit -m "changelog"`;
await $({ stdio: 'inherit' })`git push`;
await $({ stdio: 'inherit' })`git tag ${version}`;
await $({ stdio: 'inherit' })`git push origin ${version}`;
console.log('🏁⬆️🔚');
