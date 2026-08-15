const path = require('path');
const CopyPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = (env, argv) => {
  const isProduction = argv.mode === 'production';
  const isDesktop = env.target === 'desktop';

  return {
    entry: isDesktop
      ? {
          // 창마다 별도 BrowserWindow라 엔트리도 문서도 따로 간다.
          'main-window': './apps/desktop/main-window/src/main.tsx',
          overlay: './apps/desktop/overlay/src/main.tsx',
        }
      : './apps/web/src/main.tsx',
    output: {
      path: path.resolve(__dirname, isDesktop ? 'dist-desktop' : 'dist'),
      filename: isDesktop ? '[name].js' : 'bundle.js',
      publicPath: isDesktop ? './' : '/',
      clean: isDesktop ? { keep: /^electron\// } : true,
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
                  [
                    '@babel/preset-react',
                    {
                      runtime: 'automatic',
                      development: !isProduction,
                    },
                  ],
                  '@babel/preset-typescript',
                ],
              },
            },
          ],
          exclude: /node_modules/,
        },
        {
          test: /\.css$/,
          use: ['style-loader', 'css-loader', 'postcss-loader'],
        },
        {
          test: /\.(png|svg|jpg|jpeg|gif)$/i,
          type: 'asset',
        },
      ],
    },
    // 개발 모드 기본값은 eval 기반이라 CSP의 script-src 'self' 에 걸려 번들이
    // 통째로 실행되지 않는다. eval 을 쓰지 않는 소스맵으로 바꾼다.
    devtool: isProduction ? false : 'source-map',
    resolve: {
      extensions: ['.tsx', '.ts', '.js'],
    },
    plugins: isDesktop
      ? [
          // chunks 를 지정해야 각 문서가 자기 창의 번들만 싣는다.
          new HtmlWebpackPlugin({
            template: './apps/desktop/main-window/index.html',
            filename: 'main-window.html',
            chunks: ['main-window'],
            inject: true,
          }),
          new HtmlWebpackPlugin({
            template: './apps/desktop/overlay/index.html',
            filename: 'overlay.html',
            chunks: ['overlay'],
            inject: true,
          }),
        ]
      : [
          new HtmlWebpackPlugin({
            template: './apps/web/index.html',
            filename: 'index.html',
            inject: true,
          }),
          // public/ 아래 파일은 가공 없이 dist/ 루트로 복사한다. (riot.txt 등)
          // 배포는 rsync --delete라, 여기 없으면 서버에 둬도 다음 배포에 지워진다.
          new CopyPlugin({ patterns: [{ from: 'public', to: '.', noErrorOnMissing: true }] }),
        ],
    devServer: {
      port: 3000,
      open: true,
      hot: true,
      historyApiFallback: true,
      proxy: [
        {
          context: ['/recommendations'],
          // 기본은 배포된 백엔드. 로컬 서버로 붙이려면 API_TARGET 을 지정한다.
          //   $env:API_TARGET = "http://localhost:8080"
          target: process.env.API_TARGET ?? 'http://3.39.39.73',
          changeOrigin: true,
        },
      ],
      client: {
        overlay: true,
      },
    },
  };
};
