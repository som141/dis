// Nexon's ItemIcon filenames encode the seven-digit KMS item ID.
// Table: https://github.com/HikariCalyx/WzComparerR2-JMS#itemid-to-nx-openapi-itemicon-filename
const columns=['?EHGBA','PONMLKJIHG','CDABGHEFKI','LAJIPONMDC','HGFEDCBAPO','OPMNKLIJGH','BADCFEHGJI'];
const digits=columns.map(column=>new Map([...column].map((char,index)=>[char,index])));

export function officialItemIdFromIcon(url){
  const code=/\/ItemIcon\/(K[A-Z]{7})\.png(?:\?|$)/i.exec(url)?.[1]?.toUpperCase();
  if(!code)return null;
  let value=0;
  for(let index=0;index<7;index++){
    const digit=digits[index].get(code[index+1]);
    if(digit===undefined||index===0&&digit===0)return null;
    value=value*10+digit;
  }
  return value;
}
