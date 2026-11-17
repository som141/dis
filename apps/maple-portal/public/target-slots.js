// Only slots supported by the equipment damage model are offered for replacement.
export const EQUIPMENT_SLOTS=['모자','얼굴장식','눈장식','귀고리','상의','한벌옷','하의','신발','장갑','망토','무기','보조무기','엠블렘','반지1','반지2','반지3','반지4','펜던트','펜던트2','벨트','어깨장식','포켓아이템','훈장','뱃지','기계심장'];
const ACCESSORIES=['얼굴장식','눈장식','귀고리','반지1','반지2','반지3','반지4','펜던트','펜던트2','벨트','어깨장식','포켓아이템','훈장','뱃지','엠블렘'];
const ARMOR=['모자','상의','한벌옷','하의','신발','장갑','망토','보조무기'];
const DIRECT={ARMOR_ARMOR_CAP:['모자'],ARMOR_ARMOR_COAT:['상의'],ARMOR_ARMOR_LONGCOAT:['한벌옷'],ARMOR_ARMOR_PANTS:['하의'],ARMOR_ARMOR_SHOES:['신발'],ARMOR_ARMOR_GLOVE:['장갑'],ARMOR_ARMOR_SHIELD:['보조무기'],ARMOR_ARMOR_CAPE:['망토'],ARMOR_ACCESSORY_FACE:['얼굴장식'],ARMOR_ACCESSORY_EYE:['눈장식'],ARMOR_ACCESSORY_EAR:['귀고리'],ARMOR_ACCESSORY_RING:['반지1','반지2','반지3','반지4'],ARMOR_ACCESSORY_PENDANT:['펜던트','펜던트2'],ARMOR_ACCESSORY_BELT:['벨트'],ARMOR_ACCESSORY_MEDAL:['훈장'],ARMOR_ACCESSORY_SHOULDER:['어깨장식'],ARMOR_ACCESSORY_POCKET:['포켓아이템'],ARMOR_ACCESSORY_BADGE:['뱃지'],ARMOR_ACCESSORY_EMBLEM:['엠블렘'],ARMOR_ACCESSORY_POWER_SOURCE:['엠블렘'],ARMOR_ETC_MACHINE_HEART:['기계심장'],WEAPON_SUB:['보조무기']};
export function targetSlotsForCategory(category){
  if(Object.hasOwn(DIRECT,category))return [...DIRECT[category]];
  if(category==='ALL')return [...EQUIPMENT_SLOTS];
  if(category==='ARMOR')return EQUIPMENT_SLOTS.filter(slot=>slot!=='무기');
  if(category==='ARMOR_ARMOR')return [...ARMOR];
  if(category==='ARMOR_ACCESSORY')return [...ACCESSORIES];
  if(category==='ARMOR_ETC')return ['기계심장'];
  if(category==='WEAPON'||category?.startsWith('WEAPON_'))return ['무기'];
  return [];
}
export function targetSlotAllowed(category,slot){return targetSlotsForCategory(category).includes(slot);}
