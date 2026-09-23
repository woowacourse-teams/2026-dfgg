const fs = require('fs');
const path = require('path');
const CopyPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');

const RENDERER_DIR = path.resolve(__dirname, 'src/renderer');

// src/renderer 아래에서 index.tsx 를 가진 폴더가 곧 하나의 창(home, overlay ...)이다.
const windows = fs
  .readdirSync(RENDERER_DIR, { withFileTypes: true })
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name)
  .filter((name) => fs.existsSync(path.join(RENDERER_DIR, name, 'index.tsx')));

module.exports = (_env, argv) => {
  const isProduction = argv.mode === 'production';

  return {
    context: __dirname,
    entry: Object.fromEntries(windows.map((name) => [name, `./src/renderer/${name}/index.tsx`])),
    output: {
      path: path.resolve(__dirname, 'out/renderer'),
      filename: '[name]/bundle.js',
      publicPath: './',
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
      ...windows.map(
        (name) =>
          new HtmlWebpackPlugin({
            template: `./src/renderer/${name}/public/index.html`,
            filename: `${name}/index.html`,
            chunks: [name],
            inject: true,
          }),
      ),
      new CopyPlugin({
        patterns: windows.map((name) => ({
          from: `src/renderer/${name}/public`,
          to: name,
          globOptions: { ignore: ['**/index.html'] },
          noErrorOnMissing: true,
        })),
      }),
    ],
    devServer: {
      port: 3001,
      open: ['/home/index.html'],
      hot: true,
      historyApiFallback: true,
      proxy: [
        {
          context: ['/api', '/feedback'],
          target: 'http://localhost:8080',
          // target: 'http://3.36.100.9',
          changeOrigin: true,
        },
      ],
      client: {
        overlay: true,
      },
    },
  };
};
