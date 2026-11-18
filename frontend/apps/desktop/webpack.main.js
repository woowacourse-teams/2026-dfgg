const path = require('path');
const pkg = require('./package.json');

module.exports = (_env, argv) => {
  const isProduction = argv.mode === 'production';

  const base = {
    context: __dirname,
    mode: isProduction ? 'production' : 'development',
    output: {
      path: path.resolve(__dirname, 'out/main'),
      filename: '[name].js',
    },
    externals: [...Object.keys(pkg.dependencies ?? {}), 'bufferutil', 'utf-8-validate'],
    externalsType: 'commonjs',
    node: { __dirname: false, __filename: false },
    module: {
      rules: [
        {
          test: /\.ts$/,
          exclude: /node_modules/,
          use: {
            loader: 'babel-loader',
            options: {
              presets: [
                ['@babel/preset-env', { targets: { node: 'current' } }],
                '@babel/preset-typescript',
              ],
            },
          },
        },
      ],
    },
    resolve: { extensions: ['.tsx', '.ts', '.js'] },
    devtool: 'source-map',
  };

  return [
    { ...base, target: 'electron-main', entry: { main: './src/main/main.ts' } },
    { ...base, target: 'electron-preload', entry: { preload: './src/main/preload.ts' } },
  ];
};
