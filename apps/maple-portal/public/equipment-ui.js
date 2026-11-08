const make=(tag,text)=>{const e=document.createElement(tag);if(text)e.textContent=text;return e;};
export function enhanceEquipment(row,config,api,onDirty,onWatchOff=async()=>{}){
  const f=k=>row.querySelector(`[data-field="${k}"]`), panel=make('section');panel.className='equipment-picker';
  const controls=make('div');controls.className='catalog-controls';
  const jobs=make('select');jobs.dataset.field='jobClass';jobs.setAttribute('aria-label','착용 직업군');
  for(const [v,t] of [['ALL','전체 직업군'],['WARRIOR','전사'],['MAGE','마법사'],['ARCHER','궁수'],['THIEF','도적'],['PIRATE','해적']]){const o=make('option',t);o.value=v;jobs.append(o);}jobs.value=config.jobClass||'ALL';
  const search=make('input');search.placeholder='도감에서 이름·초성 검색';search.setAttribute('aria-label','도감 이름 검색');
  const count=make('p'),grid=make('div'),more=make('button','다음 장비'),previous=make('button','이전 장비'),all=make('button','이 부위 전체 검색');grid.className='equipment-grid';for(const b of [more,previous,all])b.type='button';
  const selected=make('p');selected.className='selected-equipment';controls.append(jobs,search,all);panel.append(controls,count,grid,previous,more,selected);row.prepend(panel);
  row.equipmentId=config.itemId||'';row.potentialOptions=config.potentialOptions??(config.option&&Number(config.optionMin)>0?[{option:config.option,minValue:config.optionMin}]:[]);row.additionalPotentialOptions=config.additionalPotentialOptions??(config.additionalOption&&Number(config.additionalMin)>0?[{option:config.additionalOption,minValue:config.additionalMin}]:[]);
  row.potentialMaximums=config.potentialMaximums??[];row.additionalPotentialMaximums=config.additionalPotentialMaximums??[];
  const level=make('input');level.dataset.field='equipmentLevel';level.type='number';level.min=0;level.max=250;level.value=config.equipmentLevel??250;
  const levelLabel=make('label','장비 요구 레벨 · 직접 확인');levelLabel.append(level);levelLabel.hidden=true;row.append(levelLabel);
  for(const [key,label,value] of [['budgetMin','최소 · 억',config.budgetMin??''],['budgetMax','최대 · 억',config.budgetMax??200],['budgetPercent','예산 ± %',config.budgetPercent??30]]){
    const l=make('label',label),input=make('input');input.type='number';input.min=0;input.step='0.0001';input.dataset.field=key;input.value=value;l.append(input);row.querySelector('.query-main').append(l);
  }
  const defenseLabel=make('label','대상 보스 방어율 · %'),defense=make('input');defense.type='number';defense.min=0;defense.max=1000;defense.value=config.bossDefenseRate??380;defense.dataset.field='bossDefenseRate';defenseLabel.append(defense);row.querySelector('.filter-grid').append(defenseLabel);
  const custom=make('option','최소~최대 직접 지정');custom.value='custom';f('budgetMode').append(custom);f('budgetMode').value=config.budgetMode||'max';
  for(const key of ['budgetMin','budgetMax']){const range=make('input');range.type='range';range.min=0;range.max=Math.max(400,Number(f(key).value)||0);range.step=1;range.value=Number(f(key).value)||0;range.setAttribute('aria-label',key==='budgetMin'?'최소 예산 슬라이더':'최대 예산 슬라이더');f(key).parentElement.append(range);range.oninput=()=>{f(key).value=range.value;f('budgetMode').value='custom';onDirty();};f(key).addEventListener('input',()=>{range.max=Math.max(400,Number(f(key).value)||0);range.value=f(key).value;});}
  let offset=0,generation=0,timer;
  async function load(){const gen=++generation;try{const d=await api('/api/catalog?'+new URLSearchParams({category:f('category').value,job:jobs.value,q:search.value,offset}));if(gen!==generation)return;count.textContent=`${d.total.toLocaleString()}개 · 이름 검색 시 공식 최신 후보 포함`;count.title=d.notice;grid.replaceChildren();grid.scrollTop=0;
    for(const item of d.items){const b=make('button');b.type='button';b.className='equipment-tile';const img=make('img');img.src=item.icon;img.loading='lazy';img.width=40;img.height=40;img.alt='';const unverified=item.metadataStatus==='name-only';b.append(img,make('strong',item.name),make('small',unverified?'공식 이름 · 부위 추정 · 레벨/직업 미확인':`Lv.${item.level} · ${item.jobs.join(' / ')}`));b.onclick=()=>{f('keyword').value=item.name;f('category').value=item.category;row.equipmentId=unverified?'':item.id;levelLabel.hidden=!unverified;if(unverified){level.value='';selected.textContent=`${item.name} · 공식 이름만 확인됨. 부위·레벨·직업과 경매장 거래 가능 여부를 확인한 뒤 조회하세요.`;}else{level.value=item.level;selected.textContent=`선택: ${item.name} · Lv.${item.level}`;}row.querySelector('.row-filters').open=true;onDirty();};grid.append(b);}
    more.disabled=offset+120>=d.total;previous.disabled=offset===0;
  }catch(e){count.textContent=e.message;}}
  const reset=()=>{row.equipmentId='';levelLabel.hidden=true;selected.textContent='장비와 옵션을 다시 선택하세요.';offset=0;onDirty();void load();};
  jobs.onchange=reset;f('category').addEventListener('change',reset);f('keyword').addEventListener('input',()=>{row.equipmentId='';});
  search.oninput=()=>{clearTimeout(timer);timer=setTimeout(()=>{offset=0;void load();},180);};more.onclick=()=>{offset+=120;void load();};previous.onclick=()=>{offset=Math.max(0,offset-120);void load();};all.onclick=()=>{f('keyword').value='';reset();};
  const rulesBox=make('div');rulesBox.className='potential-panels';row.querySelector('.row-filters').append(rulesBox);
  row.querySelector('.row-filters').append(make('p','성능 비교는 현재 장비를 교체했을 때의 스탯 기반 기대 피해 변화입니다. 쓸만한 스킬 부여 옵션, 시드링 발동, 쿨타임·스킬 회전율은 계산에 포함하지 않습니다. 시드링 장비 교체는 비교 미지원으로 표시합니다.'));
  let labels={},maximumOptionKeys=[];
  async function initOptions(){
    try{const data=await api('/api/catalog?category=ALL&q=__labels_only__');labels=data.optionLabels||{};maximumOptionKeys=data.maximumOptionKeys||[];}catch{rulesBox.append(make('p','옵션 목록을 불러오지 못했습니다. 화면을 새로고침해 주세요.'));return;}
    for(const [key,title,maximum] of [['potentialOptions','잠재 옵션 · 합산 최소치',false],['additionalPotentialOptions','에디 잠재 옵션 · 합산 최소치',false],['potentialMaximums','잠재 옵션 · 합산 상한',true],['additionalPotentialMaximums','에디 잠재 옵션 · 합산 상한',true]]){
      const group=make('section'),list=make('div'),add=make('button',maximum?'＋ 상한 조건 추가':'＋ 최소치 조건 추가');add.type='button';group.append(make('h4',title),make('p',maximum?'경매장에는 상한 검색이 없습니다. 불러온 매물의 실제 옵션 줄을 합산해 이 값 이하만 남깁니다. 확인할 수 없는 매물은 제외합니다.':'각 옵션은 모든 줄의 합계 기준입니다. 입력한 조건을 모두 만족하는 매물을 찾습니다.'),list,add);rulesBox.append(group);
      const render=()=>{list.replaceChildren();row[key].forEach((condition,index)=>{
        const line=make('div');line.className='option-condition';const select=make('select');select.setAttribute('aria-label',title+' 옵션');select.append(new Option('옵션 선택',''));
        for(const [value,label] of Object.entries(labels)){if(maximum&&!maximumOptionKeys.includes(value))continue;if(label.startsWith('쓸만한 ')&&value!==condition.option)continue;if((key==='potentialOptions'||key==='potentialMaximums')&&value.endsWith('PerLevel'))continue;select.append(new Option(label+((/Percent$/.test(value)||value==='ignoreMonsterDefense')&&!label.includes('%')?' %':''),value));}
        select.value=condition.option;select.onchange=()=>{condition.option=select.value;onDirty();};
        const limit=make('input');limit.type='number';limit.min=0;limit.max=10000;limit.step='any';const property=maximum?'maxValue':'minValue';limit.value=condition[property]??'';limit.setAttribute('aria-label',title+(maximum?' 상한값':' 최소값'));limit.oninput=()=>{condition[property]=limit.value;onDirty();};
        const unit=make('span');const setUnit=()=>{unit.textContent=((/Percent$/.test(select.value)||select.value==='ignoreMonsterDefense'||select.value==='criticalPercent')?'% ':select.value==='skillCooldownReduction'?'초 ':'')+(maximum?'이하':'이상');};select.addEventListener('change',setUnit);setUnit();
        const remove=make('button','×');remove.type='button';remove.setAttribute('aria-label',title+' 조건 삭제');remove.onclick=()=>{row[key].splice(index,1);onDirty();render();};
        line.append(select,limit,unit,remove);list.append(line);
      });add.disabled=row[key].length>=8;};
      add.onclick=()=>{row[key].push({option:'',[maximum?'maxValue':'minValue']:''});onDirty();render();};render();
    }
  }
  for(const key of ['potentialGrade','additionalGrade']){f(key).append(new Option('잠재 없음','none'));f(key).value=(key==='potentialGrade'?config.potentialNone:config.additionalNone)?'none':config[key]||'0';}
  f('watch').addEventListener('change',async()=>{if(f('watch').checked)return;try{await onWatchOff();}catch(e){selected.textContent='감시 중지 확인: '+e.message;}});
  void load();void initOptions();
}
