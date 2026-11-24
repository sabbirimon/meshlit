const path = require('node:path');
function launchArguments(platform, executable, folder) {
  if (!path.isAbsolute(executable) || !path.isAbsolute(folder) || /[\r\n\0]/.test(executable + folder)) throw new Error('Absolute local paths required');
  return platform === 'darwin' && executable.endsWith('.app')
    ? ['/usr/bin/open', ['-n', '-a', executable, '--args', '--workspace', folder]]
    : [executable, ['--workspace', folder]];
}
module.exports = { launchArguments };
