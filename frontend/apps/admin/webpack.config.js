const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');

const { registerAdminApi } = require('./server');

/**
 * 관리자 페이지는 로컬에서만 띄운다 (pnpm admin).
 * 우마미·스토어 키는 dev server(Node)가 .env 에서 읽어 대신 호출하고,
 * 브라우저에는 집계된 숫자만 내려준다. 배포 워크플로는 이 앱을 빌드하지 않는다.
 */
module.exports = () => ({
  mode: 'development',
  context: __dirname,
  entry: './src/main.tsx',
  output: {
    path: path.resolve(__dirname, 'dist'),
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
                ['@babel/preset-react', { runtime: 'automatic', development: true }],
                '@babel/preset-typescript',
              ],
            },
          },
        ],
        exclude: /node_modules/,
      },
      { test: /\.css$/, use: ['style-loader', 'css-loader', 'postcss-loader'] },
    ],
  },
  devtool: 'source-map',
  resolve: { extensions: ['.tsx', '.ts', '.js'] },
  plugins: [
    new HtmlWebpackPlugin({ template: './index.html', filename: 'index.html', inject: true }),
  ],
  devServer: {
    // 같은 와이파이의 다른 기기에서 열리지 않도록 이 컴퓨터에만 연다.
    host: '127.0.0.1',
    port: 3100,
    open: true,
    hot: true,
    historyApiFallback: true,
    client: { overlay: true },
    setupMiddlewares: (middlewares, devServer) => {
      registerAdminApi(devServer.app);
      return middlewares;
    },
  },
});
