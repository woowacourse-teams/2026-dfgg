const path = require('path');
const CopyPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');

const META = require('./src/i18n/meta.json');

const SITE = 'https://dfgg.pro';
const MS_STORE_URL = 'https://apps.microsoft.com/detail/9nxl98m7xc82';

/**
 * 언어마다 index.html 을 따로 만든다. 서버(nginx)는 /ko/ 에 ko/index.html 을 준다.
 * 루트(/)는 언어를 고르기 전 주소라 x-default 로 두고 한국어 내용을 담는다.
 * 루트 index.html 은 /privacy 처럼 파일이 없는 주소에도 nginx 가 대신 내주므로 canonical 을 달지 않는다.
 * 달면 그 주소들이 모두 홈과 같은 문서로 묶인다.
 */
const PAGES = [
  { filename: 'index.html', lang: 'ko', url: `${SITE}/`, isFallback: true },
  { filename: 'ko/index.html', lang: 'ko', url: `${SITE}/ko/` },
  { filename: 'en/index.html', lang: 'en', url: `${SITE}/en/` },
];

/** 검색 결과에 앱 정보(무료·Windows)가 붙도록 schema.org 구조화 데이터를 넣는다. */
const jsonLd = ({ lang, url }) => {
  const data = {
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: 'DFGG',
    url,
    description: META[lang].description,
    inLanguage: lang,
    applicationCategory: 'GameApplication',
    operatingSystem: 'Windows 10, Windows 11',
    downloadUrl: MS_STORE_URL,
    image: `${SITE}/og/og-image.jpg`,
    offers: { '@type': 'Offer', price: '0', priceCurrency: 'KRW' },
  };
  // </script> 로 태그가 끊기지 않게 < 를 이스케이프한다.
  return `<script type="application/ld+json">${JSON.stringify(data).replace(/</g, '\\u003c')}</script>`;
};

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
      ...PAGES.map(
        (page) =>
          new HtmlWebpackPlugin({
            template: './index.html',
            filename: page.filename,
            inject: true,
            templateParameters: {
              lang: page.lang,
              url: page.url,
              canonical: page.isFallback ? null : page.url,
              title: META[page.lang].title,
              description: META[page.lang].description,
              ogLocale: page.lang === 'ko' ? 'ko_KR' : 'en_US',
              jsonLd: jsonLd(page),
            },
          }),
      ),
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
