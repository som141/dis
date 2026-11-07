import {test} from 'node:test';
import assert from 'node:assert/strict';
import {summarizeItem,isAuctionItemWearable,auctionWearabilityError,setOfItem} from '../dist/core.mjs';

test('actual Dawn accessory names retain their set while Guardian ring conversion remains explicit',()=>{
 for(const name of ['에스텔라 이어링','트와일라이트 마크','데이브레이크 펜던트','여명의 가디언 엔젤 링'])assert.equal(setOfItem(name),'여명의 보스');
 assert.equal(setOfItem('가디언 엔젤 링'),'보스 장신구');assert.equal(setOfItem('커맨더 포스 이어링'),'칠흑의 보스');
});

test('public accessory job label is wearable while actual job and explicit exclusions still apply',()=>{
 for(const reqJob of ['공용','전체','전직업',null,undefined,'마법사','에반','에반 전용'])assert.equal(isAuctionItemWearable({toolTip:{reqJob}},'에반'),true);
 assert.equal(isAuctionItemWearable({toolTip:{reqJob:'전사'}},'에반'),false);
 assert.equal(isAuctionItemWearable({toolTip:{reqJob:'에반'}},'비숍'),false);
 assert.equal(isAuctionItemWearable({toolTip:{reqJob:'공용',unwearableJobNames:['에반']}},'에반'),false);
 assert.match(auctionWearabilityError({toolTip:{reqJob:'전사'}},'에반'),/요구 직업 전사/);
});
test('official Evan document secondary weapons are not rejected by broad auction job labels',()=>{
 for(const itemName of ['드래곤마스터의 유산','이볼빙 드래곤마스터의 유산','블랙 드래곤마스터의 유산','메이플 트레져의 유산']){
  assert.equal(isAuctionItemWearable({itemName,toolTip:{reqJob:'전체'}},'에반'),true,itemName);
  assert.equal(isAuctionItemWearable({itemName,toolTip:{reqJob:'마법사'}},'비숍'),false,itemName);
  assert.match(auctionWearabilityError({itemName,toolTip:{reqJob:'마법사'}},'비숍'),/에반 전용/);
 }
});
const raw=()=>({_id:'fixture:1',itemName:'에테르넬 메이지글러브',pricePerItem:'20000000000',quantity:1,starforce:17,status:'ON_SALE',endDate:'2026-11-05T00:00:00Z',wishlistCount:0,itemCode:'private-item-payload',toolTip:{itemIcon:{fallBackUrl:'https://avatar.maplestory.nexon.com/ItemIcon/TEST.png'},reqLevel:250,reqJob:'마법사',categories:['방어구','장갑'],stat:{int:120,mad:90},baseStat:{int:40,mad:80},starforceStat:{int:25,mad:10},upgradeStat:{int:20},exOptionStat:{int:35},upgradeInfo:{scroll:{current:8,remaining:0,failure:0,max:8},potential:{grade:4,description:'잠재능력 : 레전드리',entries:[{grade:4,text:'크리티컬 데미지 +8%'},{grade:4,text:'크리티컬 데미지 +8%'},{grade:3,text:'최대 HP +10%'}]},additionalPotential:{grade:3,description:'에디셔널 잠재능력 : 유니크',entries:[{grade:3,text:'INT +6%'},{grade:2,text:'INT +15'}]}},tradeDesc:['1회 교환 가능','가위 사용 잔여 횟수 : 8 / 10'],session:'not-for-client'}});
test('listing summary stays concise while tooltip preserves separate actual enhancement and potential fields',()=>{
 const s=summarizeItem(raw());assert.ok(s.potentialBrief.includes('크뎀 16%'));assert.equal(s.potentialGrade,4);assert.equal(s.additionalGrade,3);assert.equal(s.icon,'https://avatar.maplestory.nexon.com/ItemIcon/TEST.png');
 const d=s.itemDetails;assert.equal(d.stats.int,120);assert.equal(d.components.base.int,40);assert.equal(d.components.extra.int,35);assert.equal(d.components.starforce.int,25);assert.equal(d.components.scroll.int,20);assert.equal(d.potential.entries.length,3);assert.equal(d.additional.entries.length,2);assert.equal(d.scroll.remaining,0);assert.equal(d.stats.str,undefined);
 assert.equal(JSON.stringify(s).includes('private-item-payload'),false);assert.equal(JSON.stringify(s).includes('not-for-client'),false);
});
test('missing tooltip fields do not become fake zero stats; unsafe image origins are not forwarded',()=>{
 const item=raw();item.toolTip={itemIcon:{fallBackUrl:'https://evil.invalid/tracker.png'}};const s=summarizeItem(item);
 assert.equal(s.icon,undefined);assert.equal(s.itemDetails.potential,undefined);assert.equal(s.itemDetails.stats,undefined);assert.equal(s.itemDetails.level,undefined);
});

test('official auction CDN icons survive the display allowlist and unknown numeric fields stay private',()=>{
 const item=raw(),url='https://mskr-web.dn.nexoncdn.co.kr/gamefiles/icon/0123456789abcdef0123456789abcdef.png';item.toolTip.itemIcon.fallBackUrl=url;item.toolTip.stat.privateNumericMarker=12345;
 const s=summarizeItem(item);assert.equal(s.icon,url);assert.equal(s.itemDetails.stats.privateNumericMarker,undefined);
 item.toolTip.itemIcon.fallBackUrl='https://avatar.maplestory.nexon.com:8443/ItemIcon/test.png';assert.equal(summarizeItem(item).icon,undefined);
});

test('ignore-defense lines are not displayed as an additive effective value',()=>{
 const item=raw();item.toolTip.upgradeInfo.potential.entries=[{text:'몬스터 방어율 무시 +30%'},{text:'몬스터 방어율 무시 +30%'}];
 assert.equal(summarizeItem(item).potentialBrief,'방무 30% · 방무 30%');
});
