const path = require('path');
const CopyPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = (_env, argv) => {
  const isProduction = argv.mode === 'production';

  return {
    context: __dirname,
    entry: './src/main.tsx',
    output: {
      // 배포 워크플로가 frontend/dist 를 rsync 하므로 위치를 유지한다.
      path: path.resolve(__dirname, '../../dist'),
      filename: 'bundle.js',
      publicPath: '/',
      clean: true,
    },
    module: {
      rules: [
        {
          test: /\.(ts|tsx)$/,
          use: [
            {
              loader: 'babel-loader',
              options: {
                presets: [
                  '@babel/preset-env',
                  ['@babel/preset-react', { runtime: 'automatic', development: !isProduction }],
                  '@babel/preset-typescript',
                ],
              },
            },
          ],
          exclude: /node_modules/,
        },
        { test: /\.css$/, use: ['style-loader', 'css-loader', 'postcss-loader'] },
        { test: /\.(png|svg|jpg|jpeg|gif)$/i, type: 'asset' },
      ],
    },
    devtool: isProduction ? false : 'source-map',
    resolve: { extensions: ['.tsx', '.ts', '.js'] },
    plugins: [
      new HtmlWebpackPlugin({ template: './index.html', filename: 'index.html', inject: true }),
      // public/ 아래 파일은 가공 없이 dist/ 루트로 복사한다. (riot.txt 등)
      new CopyPlugin({ patterns: [{ from: '../../public', to: '.', noErrorOnMissing: true }] }),
    ],
    devServer: {
      port: 3000,
      open: true,
      hot: true,
      historyApiFallback: true,
      proxy: [
        {
          context: ['/api', '/feedback'],
          target: process.env.API_TARGET ?? 'https://dfgg.pro',
          changeOrigin: true,
        },
      ],
      client: { overlay: true },
    },
  };
};
