"""Join official auction names with explicitly versioned KMS metadata. No auction searches."""
import json, pathlib, sys, datetime, collections, re
root = pathlib.Path(__file__).resolve().parents[1]
names = json.loads((root/'public/data/official-snippets.json').read_bytes())
metadata = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding='utf-8-sig'))
slots = dict(zip(['Hat','Top','Overall','Bottom','Shoes','Glove','Shield','Cape'], ['CAP','COAT','LONGCOAT','PANTS','SHOES','GLOVE','SHIELD','CAPE']))
access = dict(zip(['Face Accessory','Eye Decoration','Earrings','Ring','Pendant','Belt','Medal','Shoulder Accessory','Pocket Item','Badge','Emblem'], ['FACE','EYE','EAR','RING','PENDANT','BELT','MEDAL','SHOULDER','POCKET','BADGE','EMBLEM']))
weapons = dict(zip(['One-Handed Sword','One-Handed Axe','One-Handed Blunt Weapon','Dagger','Cane','Wand','Staff','Shining Rod','Soul Shooter','Desperado','Whip Blade','Psy-limiter','Chain','Gauntlet','Ritual Fan','Bladecaster','Breath Shooter','Two-Handed Sword','Two-Handed Axe','Two-Handed Blunt','Spear','Pole Arm','Bow','Crossbow','Claw','Knuckle','Gun','Dual Bowgun','Hand Cannon','Arm Cannon','Ancient Bow'], ['ONE_HANDED_SWORD','ONE_HANDED_AXE','ONE_HANDED_MACE','ONE_HANDED_DAGGER','ONE_HANDED_CANE','ONE_HANDED_WAND','ONE_HANDED_STAFF','ONE_HANDED_SHINING_ROD','ONE_HANDED_SOUL_SHOOTER','ONE_HANDED_DESPERADO','ONE_HANDED_ENERGY_SWORD','ONE_HANDED_ESP_LIMITER','ONE_HANDED_CHAIN','ONE_HANDED_MAGIC_GAUNTLET','ONE_HANDED_FAN','ONE_HANDED_TUNER','ONE_HANDED_BREATH_SHOOTER','TWO_HANDED_SWORD','TWO_HANDED_AXE','TWO_HANDED_MACE','TWO_HANDED_SPEAR','TWO_HANDED_POLEARM','TWO_HANDED_BOW','TWO_HANDED_CROSSBOW','TWO_HANDED_THROWING_GLOVE','TWO_HANDED_KNUCKLE','TWO_HANDED_GUN','TWO_HANDED_DUAL_BOW','TWO_HANDED_HAND_CANNON','TWO_HANDED_GAUNTLET_REVOLVER','TWO_HANDED_ANCIENT_BOW']))
jobs={'Warrior':'WARRIOR','Magician':'MAGE','Bowman':'ARCHER','Thief':'THIEF','Pirate':'PIRATE','Beginner':'ALL'}
lookup=collections.defaultdict(list)
for x in metadata:
    t=x.get('typeInfo',{}); sub=t.get('subCategory')
    if t.get('overallCategory')!='Equip' or x.get('isCash'): continue
    category=('ARMOR_ARMOR_'+slots[sub] if sub in slots else 'ARMOR_ACCESSORY_'+access[sub] if sub in access else 'WEAPON_'+weapons[sub] if sub in weapons else {'Mechanical Heart':'ARMOR_ETC_MACHINE_HEART','Android':'ARMOR_ETC_ANDROID','Mechanic Equipment':'ARMOR_ETC_MECHANIC','Dragon Equipment':'ARMOR_ETC_DRAGON_CAP'}.get(sub,'WEAPON_SUB' if t.get('category')=='Secondary Weapon' else None))
    if category and x.get('name'): lookup[x['name']].append(dict(itemId=x['id'],category=category,level=x.get('requiredLevel'),jobs=[jobs[j] for j in x.get('requiredJobs',[]) if j in jobs],subtype=sub,gender=x.get('requiredGender')))
items=[]
for x in names:
    for m in lookup.get(x['nameOrigin'],[]): items.append(dict(id=str(m['itemId']),name=x['nameOrigin'],icon=x['thumbnail'],**m))
# Official update 749 documents these 15 level-250 additions missing from KMS 389.
source='https://maplestory.nexon.com/News/Update/749'
families={'나이트':'WARRIOR','메이지':'MAGE','아처':'ARCHER','시프':'THIEF','파이렛':'PIRATE'}
suffixes={'글러브':'ARMOR_ARMOR_GLOVE','슈즈':'ARMOR_ARMOR_SHOES','케이프':'ARMOR_ARMOR_CAPE'}
known={x['name'] for x in items}
for x in names:
    m=re.fullmatch(r'에테르넬 (나이트|메이지|아처|시프|파이렛)(글러브|슈즈|케이프)',x['nameOrigin'])
    if m and x['nameOrigin'] not in known:
        items.append(dict(id='official-'+x['encryptedItemId'],name=x['nameOrigin'],icon=x['thumbnail'],encryptedItemId=x['encryptedItemId'],category=suffixes[m[2]],level=250,jobs=[families[m[1]]],subtype={'글러브':'Glove','슈즈':'Shoes','케이프':'Cape'}[m[2]],metadataSource=source))
items=list({x['id']:x for x in items}.values())
items.sort(key=lambda x:(-(x['level'] or 0),not x['name'].startswith('에테르넬 '),x['name'],x['id']))
out=dict(source='https://mskr-web.dn.nexoncdn.co.kr/autocomplete/snippets.json',metadataSource='https://maplestory.io/api/KMS/389/item',metadataVersion='KMS 389 + official update 749',fetchedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),complete=False,notice='공식 이름 목록 + KMS 389 분류 대조. 최신 장비·거래 가능 여부 전수 확인 중',officialNameCount=len(names),items=items)
(root/'public/data/equipment.json').write_text(json.dumps(out,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
print(json.dumps(dict(equipment=len(items),categories=dict(collections.Counter(x['category'] for x in items))),ensure_ascii=False))
