import { PROJECTS } from './lib/capacitor.mjs';
import { execute } from './lib/cli.mjs';
import { root } from './lib/repo.mjs';
import { run } from './lib/subprocess.mjs';
import { getLatestVersion, setWorkspacePackageDependencies } from './lib/version.mjs';

execute(async () => {
  const packages = Object.fromEntries(
    await Promise.all(
      PROJECTS.map(async (project) => [
        project,
        `^${await getLatestVersion(project, 'latest')}`,
      ]),
    ),
  );

  await setWorkspacePackageDependencies(packages, 'devDependencies');
  await setWorkspacePackageDependencies(packages, 'peerDependencies');
  await run('pnpm', ['install'], { cwd: root, stdio: 'inherit' });
});
