const path = require('path');
const CopyPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = (env, argv) => {
  const isProduction = argv.mode === 'production';
  const isDesktop = env.target === 'desktop';

  return {
    entry: './apps/web/src/main.tsx',
    output: {
      path: path.resolve(__dirname, isDesktop ? 'dist-desktop' : 'dist'),
      filename: 'bundle.js',
      publicPath: isDesktop ? './' : '/',
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
    resolve: {
      extensions: ['.tsx', '.ts', '.js'],
    },
    plugins: [
      new HtmlWebpackPlugin({
        template: './apps/web/index.html',
        filename: 'index.html',
        inject: true,
      }),
      // public/ 아래 파일은 가공 없이 dist/ 루트로 복사한다. (riot.txt 등)
      // 배포는 rsync --delete라, 여기 없으면 서버에 둬도 다음 배포에 지워진다.
      new CopyPlugin({
        patterns: [{ from: 'public', to: '.', noErrorOnMissing: true }],
      }),
    ],
    devServer: {
      port: 3000,
      open: true,
      hot: true,
      historyApiFallback: true,
      proxy: [
        {
          context: ['/recommendations'],
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      ],
      client: {
        overlay: true,
      },
    },
  };
};
