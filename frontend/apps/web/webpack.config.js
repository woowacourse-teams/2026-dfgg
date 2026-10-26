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
      // 내용이 바뀔 때만 이름이 바뀌므로 서버가 오래 캐시해도 된다. index.html 만 매번 새로 받는다.
      filename: isProduction ? '[name].[contenthash:8].js' : '[name].js',
      chunkFilename: isProduction ? '[name].[contenthash:8].js' : '[name].js',
      publicPath: '/',
      clean: true,
    },
    // 자주 안 바뀌는 라이브러리를 앱 코드와 다른 파일로 나눠, 배포해도 라이브러리는 캐시에서 쓰게 한다.
    optimization: { splitChunks: { chunks: 'all' } },
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
        { test: /\.woff2$/i, type: 'asset/resource' },
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
