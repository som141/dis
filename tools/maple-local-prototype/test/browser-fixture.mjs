// Explicit test-only server. It never fetches Nexon character APIs.
// A Node WebSocket peer simulates the paired extension. No browser policy is changed.
import { WebSocket } from 'ws';
import { createApp } from '../src/server.mjs';
import { PrototypeEngine } from '../src/domain.mjs';
const snapshot = async () => ({
  name:'에반이였솜', job:'에반', level:280,
  equipment:{ item_equipment:[{ item_equipment_slot:'장갑', item_name:'테스트용 현재 장갑', starforce:'17', potential_option_grade:'레전드리' }] },
  stats:{ 기본:{ 마력:100, 방무:[100] }, AP:{ INT:1000, LUK:100 }, 장비:{ 장갑:{ name:'현재 장갑', stat:{ INT:100, INT퍼:21, 마력:10 } } }, 세트효과:{} },
});
const app = createApp({ port:8878, engineFactory:wire => new PrototypeEngine(wire, { loadSnapshot:snapshot, refreshSnapshot:snapshot }) });
await app.listen();
let posts = 0;
const ws = new WebSocket(`ws://127.0.0.1:8878/bridge?token=${app.bridge.token}`, { origin:`chrome-extension://${'a'.repeat(32)}` });
ws.on('error', () => {});
ws.on('message', raw => {
  const cmd = JSON.parse(String(raw)), url = new URL(cmd.url);
  let data = {};
  if (url.pathname === '/v1/accounts') data = { accounts:[{ accountId:111 }] };
  else if (url.pathname.endsWith('/characters')) data = { characters:url.pathname.includes('/gameWorlds/0/') ? [{ characterId:222, characterName:'에반이였솜', level:280 }] : [] };
  else if (url.pathname.endsWith('/daily-limit')) data = { search:{ limit:100, remaining:100 - posts } };
  else if (url.pathname.endsWith('/balance')) data = { meso:10000000000, maplePoint:0 };
  else if (url.pathname.includes('/searches/')) {
    if (cmd.method === 'POST') ++posts;
    const item = (id, price, mad) => ({ _id:id, itemName:'아케인셰이드 메이지글러브', pricePerItem:String(price), quantity:1, starforce:17, status:'ON_SALE', endDate:'2026-11-01T00:00:00Z', wishlistCount:0,
      toolTip:{ categories:['방어구', '장갑'], reqJob:'마법사', stat:{ int:100, mad }, upgradeInfo:{ potential:{ grade:4, description:'잠재능력 : 레전드리', entries:[{ text:'INT +12%' }, { text:'INT +9%' }] } } } });
    data = { searchKey:'test-only-search', page:1, total:2, totalPages:1, hasNext:false, items:[item('test-A',2500000000,21),item('test-B',3500000000,15)] };
  }
  ws.send(JSON.stringify({ id:cmd.id, ok:true, status:200, bodyText:JSON.stringify(data) }));
});
console.log('TEST FIXTURE ONLY on 127.0.0.1:8878; no real account or character data.');
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => { ws.close(); void app.close().then(() => process.exit(0)); });
