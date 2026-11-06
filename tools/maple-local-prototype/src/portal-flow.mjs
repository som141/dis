import { createHash } from 'node:crypto';
import { BrowserAutoConnect } from './auto-connect.mjs';
import { PrototypeEngine, parseQuery, parseMaximums, CATEGORIES, AppError } from './domain.mjs';
import { withoutFragmentWork } from './portal-store.mjs';
import { catalog } from './equipment-data.mjs';

export const DEFAULT_PLAN=Object.freeze({budget:200,starforceMin:0,potentialGrade:0,days:0});
const digest=value=>createHash('sha256').update(value).digest('hex');
const iso=value=>new Date(value).toISOString();
const catalogIcons=new Map(catalog.items.map(item=>[item.name,item.icon]));
const withIcon=item=>item.icon?item:{...item,icon:catalogIcons.get(item.name)};
const messages={INVALID_RESPONSE:'경매장 응답 형식이 달라 결과를 해석하지 못했습니다. 연결 모듈 점검이 필요합니다.',INVALID_QUERY:'경매장이 검색 조건을 거절했습니다. 조건을 확인하고 직접 다시 조회하세요.',CANCELLED:'조회를 중지했습니다.',QUOTA_EXHAUSTED:'오늘 검색 횟수를 모두 사용했습니다. 자정 이후 다시 조회해 주세요.',QUOTA_UNKNOWN:'검색 잔여량을 확인하지 못했습니다.',INCOMPLETE_RESULTS:'일부 페이지가 누락됐습니다. 부분 결과를 확인한 뒤 다시 조회해 주세요.',CONNECTION_CHANGED:'로그인 연결이 바뀌었습니다. 같은 계정으로 다시 연결해 주세요.',NO_KEY:'성능 비교용 서비스 키 설정이 필요합니다. 성능 비교를 끄면 매물·시세 조회는 가능합니다.',PROVIDER_ERROR:'경매장 요청에 실패했습니다. 조건이나 공식 로그인 상태를 확인해 주세요.'};
export function normalizeJobs(rows) {
  if(!Array.isArray(rows)||!rows.length) throw new AppError('검색할 아이템을 추가해 주세요.');
  const ids=new Set();
  return rows.map((row,index)=>{
    if(!row||typeof row!=='object') throw new AppError('검색 조건을 확인하세요.');
    if(row.mode!=null&&!['live','sold'].includes(row.mode))throw new AppError('조회 방식은 판매 중 또는 판매 완료를 선택하세요.');
    const id=typeof row.id==='string'&&/^[a-zA-Z0-9_-]{1,60}$/.test(row.id)?row.id:`item-${index+1}`;
    const mode=row.mode==='sold'?'sold':'live',budgetMode=row.budgetMode||'max';
    const jobId=id+'-'+mode;
    if(ids.has(jobId)) throw new AppError('같은 조회 방식의 검색 항목이 중복되었습니다.');ids.add(jobId);
    const result={id,enabled:row.enabled!==false,keyword:String(row.keyword||'').trim(),category:row.category||'ALL',
      targetStatus:row.targetStatus===true,bossDefenseRate:Number(row.bossDefenseRate??380),budget:Number(row.budget??0),budgetMode,budgetMin:row.budgetMin,budgetMax:row.budgetMax,budgetPercent:row.budgetPercent??30,jobClass:row.jobClass||'ALL',itemId:row.itemId||'',equipmentLevel:row.equipmentLevel??250,potentialNone:row.potentialNone===true,additionalNone:row.additionalNone===true,potentialOptions:row.potentialOptions,additionalPotentialOptions:row.additionalPotentialOptions,potentialMaximums:row.potentialMaximums,additionalPotentialMaximums:row.additionalPotentialMaximums,additionalOption:row.additionalOption||'',additionalMin:row.additionalMin??0,starforceMin:Number(row.starforceMin??0),potentialGrade:Number(row.potentialGrade??0),
      additionalGrade:Number(row.additionalGrade??0),option:row.option||'',optionMin:row.optionMin??0,
      exactMatch:row.exactMatch!==false,myWorldOnly:row.myWorldOnly!==false,mode,sold:mode==='sold',
      compare:mode!=='sold'&&row.compare===true,watch:mode!=='sold'&&row.watch===true,
      intervalMinutes:Number(row.intervalMinutes??30),days:Number(row.days??0),minGain:Number(row.minGain??0)};
    if(result.enabled){const q=parseQuery(result),maximums=parseMaximums(result);result.potentialOptions=q.potentialOptions||[];result.additionalPotentialOptions=q.additionalPotentialOptions||[];Object.assign(result,maximums);}
    if(result.enabled&&(!Number.isInteger(result.intervalMinutes)||result.intervalMinutes<1||result.intervalMinutes>1440||!Number.isInteger(result.days)||result.days<0||result.days>365||!Number.isFinite(result.minGain)||result.minGain<0)) throw new AppError('감시 간격·기간·최소 개선율을 확인하세요.');
    if(result.enabled&&result.minGain>0&&!result.compare) throw new AppError('최소 개선율을 쓰려면 성능 비교를 선택하세요.');
    return result;
  });
}
function metadata(result) {
  if(!result) return null;
  const {items,searchKey,...rest}=result;
  return {...rest,loadedCount:items.length};
}
function legacyConfig(saved) {
  const input=saved.watch?.input||{};
  return {id:'legacy-glove',enabled:true,keyword:input.keyword||'에테르넬 메이지글러브',category:input.category||'ARMOR_ARMOR_GLOVE',
    budget:saved.plan?.budget||200,budgetMode:input.budgetMode||'range',starforceMin:input.starforceMin??17,potentialGrade:input.potentialGrade??4,
    exactMatch:input.exactMatch!==false,myWorldOnly:input.myWorldOnly!==false,mode:'live',compare:true,watch:!!saved.watch,
    intervalMinutes:(saved.watch?.intervalMs||1800000)/60000,days:saved.plan?.days||1,minGain:saved.watch?.minGain||0};
}
export class PortalFlow {
  constructor(engine,wire,{saved={},save=async()=>{},now=Date.now,isOwnerAvailable=()=>true}={}) {
    this.engine=engine;this.wire=wire;this.saved=withoutFragmentWork(saved);this.save=save;this.now=now;this.isOwnerAvailable=isOwnerAvailable;this.running=false;this.phase='login';this.error=null;
    this.migrated = saved.version !== 2 || saved !== this.saved;
    saved = this.saved;
    this.configs = saved.version === 2 ? saved.configs || [] : saved.plan || saved.watch ? [legacyConfig(saved)] : [];
    const retainedJobs = saved.version === 2 ? saved.jobs || [] : [];
    this.closing = false;
    // Existing conditions are kept for review. The new form never creates a
    // search merely because a user logs in or installs this version.
    this.jobs=retainedJobs.map(job=>({...job,
      ...(['searching','equipment'].includes(job.phase)&&job.attemptedAt&&!job.watch ? {phase:'check-required',error:'이전 검색의 완료 여부를 확인할 수 없습니다. 직접 다시 검색해 주세요.'} : {}),
      engine:null,stopRequested:false}));
    this.writeQueue=Promise.resolve();
    this.auto=new BrowserAutoConnect(engine,wire,{now,chooseCharacter:characters=>{
      if(this.saved.characterId) return characters.filter(c=>c.characterId===this.saved.characterId);
      return [];
    }});
  }
  persist() {
    const snapshot={version:2,owner:this.saved.owner,characterId:this.saved.characterId,characterWorkspaces:this.saved.characterWorkspaces||{},configs:this.configs,
      jobs:this.jobs.map(({engine,stopRequested,...job})=>job)};
    this.saved=snapshot;
    this.writeQueue=this.writeQueue.catch(()=>{}).then(()=>this.save(structuredClone(snapshot)));
    return this.writeQueue;
  }
  connectionChanged() {
    for(const job of this.jobs) {
      if(job.engine) {job.engine.cancelRequested=true;job.engine.connectionChanged();}
      if(job.watch&&!job.stopped) job.phase='login';
    }
  }
  async selectCharacter(id) {
    if(this.running||this.engine.busy||this.auto.running)throw new AppError('진행 중인 작업이 끝난 뒤 캐릭터를 바꿔 주세요.',409,'BUSY');
    const characterId=Number(id);
    if(!Number.isSafeInteger(characterId)||!this.engine.characters.some(c=>c.characterId===characterId))throw new AppError('현재 계정의 캐릭터를 목록에서 선택해 주세요.');
    const owner=this.wire.accounts?digest(String(this.wire.accounts)):null;
    if(!owner||this.saved.owner&&this.saved.owner!==owner)throw new AppError('같은 계정으로 다시 연결한 뒤 캐릭터를 선택해 주세요.',409,'ACCOUNT_CHANGED');
    if(!this.isOwnerAvailable(owner))throw new AppError('이 넥슨 계정은 다른 Discord 사용자에게 연결되어 있습니다.',409,'ACCOUNT_LINKED');
    if(this.engine.character?.characterId===characterId)return;
    const previousId=this.engine.character?.characterId??this.saved.characterId;
    const changingCharacter=Boolean(previousId&&previousId!==characterId);
    const workspaces={...this.saved.characterWorkspaces};
    const jobs=this.jobs.map(job=>{
      const {engine,stopRequested,...stored}=job;
      return {...stored,watch:null,stopped:true,phase:'paused',error:'캐릭터 변경으로 감시를 중지했습니다. 계속하려면 직접 다시 조회해 주세요.'};
    });
    if(changingCharacter)workspaces[previousId]={configs:this.configs,jobs};
    const next=changingCharacter?workspaces[characterId]||{configs:[],jobs:[]}:{configs:this.configs,jobs:this.jobs};
    await this.engine.selectCharacter(characterId);
    if(changingCharacter)for(const job of this.jobs){if(job.engine){job.engine.stopWatch();job.engine.cancelRequested=true;}}
    this.configs=structuredClone(next.configs);
    this.jobs=changingCharacter?structuredClone(next.jobs).map(job=>({...job,engine:null,stopRequested:false,watch:null,stopped:true,phase:'paused'})):this.jobs;
    this.saved.owner=owner;this.saved.characterId=characterId;this.saved.characterWorkspaces=workspaces;
    this.auto.phase='ready';this.auto.error=null;this.auto.lastConnectedAt=iso(this.now());this.phase='ready';
    await this.persist();
  }
  async configure(rows) {
    if(this.running) throw new AppError('현재 조회가 끝나거나 중지된 뒤 실행해 주세요.',409,'BUSY');
    const configs=normalizeJobs(rows);
    if(!configs.some(row=>row.enabled)) throw new AppError('검색할 아이템을 하나 이상 선택하세요.');
    this.configs=[...this.configs.filter(old=>!configs.some(c=>c.id===old.id&&c.mode===old.mode)),...configs];
    const selected=new Set(configs.filter(config=>config.enabled).map(config=>config.id+'-'+config.mode));
    const previous=this.jobs.filter(job=>!selected.has(job.id));
    for(const job of this.jobs) if(selected.has(job.id)) {
      if(job.engine) {job.engine.cancelRequested=true;job.engine.stopWatch();}
      this.checkpoint(job);job.stopped=true;job.phase='paused';
    }
    this.jobs=[...previous,...configs.filter(config=>config.enabled).map(config=>({id:config.id+'-'+config.mode,config,phase:'queued',stopped:false,attemptedAt:null,completedAt:null,error:null,watch:null,notices:[],result:null,engine:null,stopRequested:false}))];
    this.error=null;await this.persist();
  }
  async prepare(job) {
    if(!job.engine) job.engine=new PrototypeEngine(this.wire,{now:this.now,loadSnapshot:this.engine.loadSnapshot,
      refreshSnapshot:this.engine.refreshSnapshot,manageKeyEnvironment:false,completePageLimit:10000});
    const e=job.engine;
    // Work runs serially, with the verified owned identity selected by auto-connect.
    e.key=this.engine.key;e.characters=this.engine.characters;e.character=this.engine.character;e.api=this.engine.api;e.service=this.engine.service;
    e.profile=this.engine.profile;e.profileAt=this.engine.profileAt;
    e.notices=job.notices||[];
    if(job.config.compare) {
      if(!e.key) throw new AppError(messages.NO_KEY,422,'NO_KEY');
      if(!this.engine.profile) {this.phase='equipment';job.phase='equipment';await this.engine.equipment();e.profile=this.engine.profile;e.profileAt=this.engine.profileAt;}
    }
    return e;
  }
  async execute(job) {
    if(job.stopRequested||job.stopped) return;
    if(job.attemptedAt&&!job.completedAt) {job.phase='check-required';job.error='이전 검색의 완료 여부가 불확실합니다. 다시 검색을 직접 눌러 주세요.';return;}
    let e;
    try {
      e=await this.prepare(job);
      if(job.stopRequested) return;
      job.phase='searching';this.phase='searching';job.attemptedAt=iso(this.now());job.error=null;
      await this.persist();
      if(job.stopRequested||job.stopped)return;
      const result=await e.run(()=>e.search(job.config));
      job.result=metadata(result);job.completedAt=result.fetchedAt;
      if(job.stopRequested) {job.phase='paused';job.stopped=true;return;}
      if(job.config.watch) {
        e.startWatch({days:job.config.days,minGain:job.config.minGain,intervalMinutes:job.config.intervalMinutes});
        job.phase='watching';
      } else job.phase=job.config.mode==='sold'?'market-ready':'complete';
      this.checkpoint(job);
    } catch(error) {
      if(e?.result) this.checkpoint(job);
      job.phase=error.code==='CANCELLED'||job.stopRequested?'paused':error.code==='NO_KEY'?'operator-setup':'check-required';
      job.error=messages[error.code]||(error instanceof AppError&&error.status<500?error.message:messages.PROVIDER_ERROR);
      if(job.phase==='paused') job.stopped=true;
    } finally {await this.persist();}
  }
  checkpoint(job) {
    const e=job.engine;if(!e) return;
    if(e.result) job.result=metadata(e.result);
    if(e.watch) job.watch={...e.watch,seen:[...e.watch.seen]};
    job.notices=e.notices.slice(0,100);
  }
  async tick() {
    if(this.running||this.engine.busy||this.closing) return;
    this.running=true;
    try {
      await this.auto.tick();
      const observedOwner=this.wire.accounts?digest(String(this.wire.accounts)):null;
      if(observedOwner&&this.saved.owner&&this.saved.owner!==observedOwner) {
        this.phase='account-changed';
        for(const job of this.jobs) {job.stopped=true;job.phase='account-changed';job.engine?.stopWatch();if(job.engine)job.engine.result=null;job.result=null;}
        await this.persist();return;
      }
      if(observedOwner&&!this.isOwnerAvailable(observedOwner)){
        this.phase='account-changed';this.error='이 넥슨 계정은 다른 Discord 사용자에게 연결되어 있습니다.';
        for(const job of this.jobs){job.stopped=true;job.phase='account-changed';job.engine?.stopWatch();if(job.engine)job.engine.result=null;job.result=null;}
        await this.persist();return;
      }

      if(this.auto.phase!=='ready'||!this.engine.character) {
        this.phase=this.auto.phase==='choose-character'?'character-unavailable':this.auto.phase==='upgrade-required'?'provider-update':'login';return;
      }
      const owner=this.wire.accounts?digest(String(this.wire.accounts)):null;
      if(!owner) {this.phase='login';return;}
      if(!this.saved.owner||!this.saved.characterId||this.migrated) {this.saved.owner=owner;this.saved.characterId=this.engine.character.characterId;this.migrated=false;await this.persist();}
      this.phase='ready';
      for(const job of this.jobs) {
        if(this.closing) break;
        if(job.stopped||job.stopRequested) continue;
        if(job.phase==='operator-setup'&&this.engine.key) {job.phase='queued';job.error=null;}
        if(job.phase==='queued') {await this.execute(job);continue;}
        if(job.config.watch!==true||!job.watch||['종료','만료'].includes(job.watch.status)) continue;
        if(job.watch.expiresAt!=null&&job.watch.expiresAt<=this.now()) {job.phase='expired';job.watch.status='만료';await this.persist();continue;}
        if(this.now()<job.watch.nextPollAt) {job.phase='watching';continue;}
        try {
          const e=await this.prepare(job);
          if(!e.watch||e.watch.status==='인증 필요') e.watch={...job.watch,seen:new Set(job.watch.seen)};
          if(e.watch.status==='한도 대기') e.watch.status='감시 중';
          if(e.watch.status!=='감시 중') {job.phase='watch-paused';continue;}
          // Persist the next poll before a potentially charged POST.
          job.watch.nextPollAt=this.now()+job.watch.intervalMs;job.pollAttemptedAt=iso(this.now());await this.persist();
          this.phase='searching';job.phase='searching';e.lastInput={...job.config};
          await e.tick();this.checkpoint(job);
          job.phase=e.watch.status==='감시 중'?'watching':e.watch.status==='만료'?'expired':'watch-paused';
          job.error=e.watch.error?(messages[e.watch.error]||messages.PROVIDER_ERROR):null;
          if(job.stopRequested) {e.stopWatch();this.checkpoint(job);job.phase='paused';job.stopped=true;}
          await this.persist();
        } catch(error) {job.phase='watch-paused';job.error=messages[error.code]||messages.PROVIDER_ERROR;await this.persist();}
      }
      this.phase=this.jobs.some(job=>job.phase==='watching')?'watching':'ready';
    } finally {this.running=false;}
  }
  async stop(id) {
    const jobs=id?this.jobs.filter(job=>job.id===id):this.jobs;
    if(!jobs.length&&id) throw new AppError('검색 항목을 찾지 못했습니다.',404);
    for(const job of jobs) {job.config.watch=false;const config=this.configs.find(c=>c.id===job.config.id&&c.mode===job.config.mode);if(config)config.watch=false;job.stopRequested=true;job.stopped=true;if(job.engine){job.engine.cancelRequested=true;job.engine.stopWatch();}job.phase=job.engine?.busy?'stopping':'paused';this.checkpoint(job);}
    await this.persist();
  }
  async remove(id) {
    const job=this.jobs.find(job=>job.id===id);if(!job)throw new AppError('검색 항목을 찾지 못했습니다.',404);
    const stopping=this.stop(id);
    this.jobs=this.jobs.filter(row=>row!==job);
    this.configs=this.configs.filter(config=>!(config.id===job.config.id&&config.mode===job.config.mode));
    // An in-flight request may finish, but cannot reinsert this job or schedule a poll.
    await stopping;
    await this.persist();
  }
  async clearFinished() {
    const removable=this.jobs.filter(job=>job.stopped||['complete','market-ready','check-required','paused','expired','account-changed'].includes(job.phase));
    for(const job of removable)await this.remove(job.id);
    return removable.length;
  }
  itemDetail(id,itemId) {
    const job=this.jobs.find(job=>job.id===id);if(!job)throw new AppError('삭제되었거나 없는 작업입니다.',404);
    if(this.phase==='account-changed')throw new AppError('연결 계정을 확인하세요.',403);
    if(!job.engine?.result)throw new AppError('상세 정보가 메모리에 없습니다. 매물을 다시 조회해 주세요.',409,'NO_RESULTS');
    const item=job.engine.result.items.find(item=>item.id===itemId);if(!item)throw new AppError('이 조회 결과에 없는 매물입니다.',404);
    return withIcon(item);
  }
  async retry(id) {
    if(this.running) throw new AppError('처리 중인 조회가 끝난 뒤 다시 시도해 주세요.',409,'BUSY');
    if(this.phase==='account-changed') throw new AppError('기존 계정으로 다시 연결하세요.',409,'ACCOUNT_CHANGED');
    const jobs=id?this.jobs.filter(job=>job.id===id):this.jobs;
    if(id&&!jobs.length)throw new AppError('검색 항목을 찾지 못했습니다.',404);
    for(const job of jobs) {job.engine?.stopWatch();job.engine=null;job.stopped=false;job.stopRequested=false;job.phase='queued';job.attemptedAt=null;job.completedAt=null;job.watch=null;job.error=null;job.result=null;}
    await this.persist();
  }
  items(id,{page=1,size=50,sort='default'}={}) {
    const job=this.jobs.find(job=>job.id===id);if(!job) throw new AppError('검색 항목을 찾지 못했습니다.',404);
    if(this.phase==='account-changed') throw new AppError('연결 계정을 확인하세요.',403);
    page=Number(page);size=Number(size);
    if(!Number.isInteger(page)||page<1||![25,50,100,200].includes(size)) throw new AppError('페이지 설정을 확인하세요.');
    let items=job.engine?.result?.items||[];
    if(sort==='price-asc') items=[...items].sort((a,b)=>a.price-b.price);
    else if(sort==='price-desc') items=[...items].sort((a,b)=>b.price-a.price);
    else if(sort==='gain-desc') items=[...items].sort((a,b)=>(b.comparison?.gainPercent??-Infinity)-(a.comparison?.gainPercent??-Infinity));
    else if(sort==='power-desc') items=[...items].sort((a,b)=>{
      const x=Number.isFinite(a.powerDiff)?a.powerDiff:null,y=Number.isFinite(b.powerDiff)?b.powerDiff:null;
      if(x===null)return y===null?0:1;if(y===null)return -1;return y-x;
    });
    return {items:items.slice((page-1)*size,page*size).map(({itemDetails,comparisonDetails,...item})=>({...withIcon(item),...(item.comparison?{comparison:{...item.comparison,details:undefined}}:{})})),page,size,total:items.length,pages:Math.ceil(items.length/size),available:!!job.engine?.result};
  }
  state() {
    const c=this.engine.character;
    return {phase:this.phase,error:this.error,busy:this.running,character:c?{id:c.characterId,name:c.name,world:c.worldName||c.world,level:c.level}:null,
      characters:this.engine.characters.map(c=>({id:c.characterId,name:c.name,world:c.worldName||c.world,level:c.level})),
      equipment:this.engine.profile,equipmentAt:this.engine.profileAt,categories:CATEGORIES,configs:this.configs,
      jobs:this.jobs.map(({engine,stopRequested,watch,notices,archive,archiveScope,...job})=>({...job,result:engine?.result?metadata(engine.result):job.result,
        hasItems:!!engine?.result,progress:engine?.busy?engine.collection:null,
        watch:engine?.watch?engine.watchView():watch?{...watch,seen:undefined,input:undefined,seenCount:watch.seen?.length||0}:null,
      notices:notices||[]})),characterPolicy:'계정 캐릭터 직접 선택 · 언제든 변경 가능'};
  }
}
