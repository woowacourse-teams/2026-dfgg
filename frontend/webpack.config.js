const path = require('path');
const CopyPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = (env, argv) => {
  const isProduction = argv.mode === 'production';
  const isDesktop = env.target === 'desktop';

  return {
    entry: isDesktop ? './apps/desktop/main-window/src/main.tsx' : './apps/web/src/main.tsx',
    output: {
      path: path.resolve(__dirname, isDesktop ? 'dist-desktop' : 'dist'),
      filename: 'bundle.js',
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
    resolve: {
      extensions: ['.tsx', '.ts', '.js'],
    },
    plugins: [
      new HtmlWebpackPlugin({
        template: isDesktop ? './apps/desktop/main-window/index.html' : './apps/web/index.html',
        filename: isDesktop ? 'main-window.html' : 'index.html',
        inject: true,
      }),
      ...(isDesktop
        ? []
        : [new CopyPlugin({ patterns: [{ from: 'public', to: '.', noErrorOnMissing: true }] })]),
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
