import { PROJECTS, PEERPROJECTS } from './lib/capacitor.mjs';
import { execute } from './lib/cli.mjs';
import { root } from './lib/repo.mjs';
import { run } from './lib/subprocess.mjs';
import { setWorkspacePackageDependencies } from './lib/version.mjs';

execute(async () => {
  const packages = Object.fromEntries(PROJECTS.map((project) => [project, process.argv[2]]));
  const peerPackages = Object.fromEntries(PEERPROJECTS.map((project) => [project, process.argv[2]]));

  await setWorkspacePackageDependencies(packages, 'devDependencies');
  await setWorkspacePackageDependencies(peerPackages, 'peerDependencies');
  await run('pnpm', ['install'], { cwd: root, stdio: 'inherit' });
});
