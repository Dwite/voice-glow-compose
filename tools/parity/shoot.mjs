// Screenshots the original voice-glow in real time, one picture per case, into out/original.
// Needs Chrome: set CHROME to its path if it is not the macOS default.
import puppeteer from 'puppeteer-core';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { mkdirSync } from 'node:fs';
import { extname, join } from 'node:path';

const root = new URL('.', import.meta.url).pathname;
const types = { '.html': 'text/html', '.js': 'text/javascript' };
const server = createServer(async (req, res) => {
  try {
    const path = join(root, decodeURIComponent(new URL(req.url, 'http://x').pathname));
    const body = await readFile(path);
    res.writeHead(200, { 'content-type': types[extname(path)] ?? 'application/octet-stream' });
    res.end(body);
  } catch {
    res.writeHead(404).end();
  }
}).listen(0);
const port = server.address().port;

const hosts = [
  { name: 'mobile', type: 'mobile', width: 393, height: 851, radius: 0 },
  { name: 'standard', type: 'default', width: 350, height: 120, radius: 20 },
  { name: 'pill', type: 'pill', width: 150, height: 44, radius: 22 },
];
const levels = { '000': 0, '015': 0.15, '035': 0.35, '100': 1 };

mkdirSync(join(root, 'out/original'), { recursive: true });
const browser = await puppeteer.launch({
  executablePath: process.env.CHROME ?? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  headless: 'new',
  args: ['--hide-scrollbars'],
});
const shoot = async (name, host, query) => {
  const page = await browser.newPage();
  await page.setViewport({ width: host.width, height: host.height, deviceScaleFactor: 2 });
  await page.goto(`http://localhost:${port}/index.html?type=${host.type}&radius=${host.radius}&${query}`, { waitUntil: 'networkidle0' });
  // The glow follows its level with an envelope: let it arrive. A virtual clock would freeze it.
  await new Promise((done) => setTimeout(done, 4000));
  await page.screenshot({ path: join(root, 'out/original', `${name}.png`) });
  await page.close();
  console.log(name);
};
for (const host of hosts) {
  for (const theme of ['dark', 'light']) {
    for (const [label, level] of Object.entries(levels)) {
      await shoot(`${host.name}_${theme}_${label}`, host, `theme=${theme}&level=${level}`);
    }
  }
}
for (const variant of ['colorful', 'mono', 'ocean', 'sunset', 'forest', 'candy', 'ice', 'gold']) {
  await shoot(`standard_dark_${variant}`, hosts[1], `theme=dark&level=0.6&variant=${variant}`);
}
await browser.close();
server.close();
